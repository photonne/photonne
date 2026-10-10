import type { ApiError, UploadAssetResponse } from '#lib/api/index.js';

/** Why an upload didn't make it, in terms the UI can explain. */
export type UploadFailure =
	'tooLarge' | 'quota' | 'password' | 'gone' | 'forbidden' | 'network' | 'server';

export class UploadError extends Error {
	constructor(readonly failure: UploadFailure) {
		super(failure);
	}
}

export interface UploadResult {
	/** The asset on the server (absent for anonymous share uploads). */
	assetId: string | null;
	/** The server already had this exact file. */
	duplicate: boolean;
}

export interface UploadOptions {
	onprogress: (loaded: number) => void;
	signal: AbortSignal;
}

/** Where a queue sends its files: the user's library or a share link. */
export interface UploadTransport {
	/** checksum → existing assetId, for the files already on the server. */
	precheck?: (checksums: string[]) => Promise<Record<string, string>>;
	upload: (file: File, options: UploadOptions) => Promise<UploadResult>;
}

export interface XhrResponse {
	status: number;
	body: unknown;
}

/**
 * A multipart POST with upload progress, which `fetch` can't report. Status 0
 * means the request never got an answer (offline, aborted by the network).
 */
export function postForm(
	url: string,
	form: FormData,
	options: UploadOptions & { headers?: Record<string, string> }
): Promise<XhrResponse> {
	return new Promise((resolve, reject) => {
		const xhr = new XMLHttpRequest();
		xhr.open('POST', url);
		for (const [name, value] of Object.entries(options.headers ?? {})) {
			xhr.setRequestHeader(name, value);
		}
		xhr.upload.onprogress = (event) => options.onprogress(event.loaded);
		xhr.onload = () => {
			let body: unknown = null;
			try {
				body = xhr.responseText ? JSON.parse(xhr.responseText) : null;
			} catch {
				// Not JSON (a proxy's error page): the status says enough.
			}
			resolve({ status: xhr.status, body });
		};
		xhr.onerror = () => resolve({ status: 0, body: null });
		xhr.onabort = () => reject(new DOMException('Aborted', 'AbortError'));
		if (options.signal.aborted) {
			reject(new DOMException('Aborted', 'AbortError'));
			return;
		}
		options.signal.addEventListener('abort', () => xhr.abort(), { once: true });
		xhr.send(form);
	});
}

/** The failure a non-2xx answer stands for (see UploadAssetsEndpoint / ShareUploadEndpoint). */
export function failureOf(response: XhrResponse): UploadFailure {
	const code = (response.body as Partial<ApiError> | null)?.code;
	if (response.status === 0) return 'network';
	if (response.status === 413) return 'tooLarge';
	if (response.status === 409 && code === 'storage_quota_exceeded') return 'quota';
	if (response.status === 401) return 'password';
	if (response.status === 403) return 'forbidden';
	if (response.status === 404 || response.status === 410) return 'gone';
	return 'server';
}

/** Capture dates travel as epoch milliseconds (ParseClientTimestamp on the server). */
export function appendDates(form: FormData, file: File) {
	if (file.lastModified > 0) {
		const millis = String(file.lastModified);
		// The browser only knows the modification time; the camera's own date
		// is read from EXIF on the server, this is the fallback for files
		// without it (screenshots, some videos).
		form.append('fileCreatedAt', millis);
		form.append('fileModifiedAt', millis);
	}
}

export function isDuplicateMessage(body: unknown): boolean {
	const message = (body as Partial<UploadAssetResponse> | null)?.message ?? '';
	return /already exists/i.test(message);
}
