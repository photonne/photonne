import {
	downloadAssetsZip,
	getAssetsDownloadOptions,
	type DownloadOptionsResponse
} from '#lib/api/index.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { fileStamp } from '#lib/format.js';
import { m } from '#lib/paraglide/messages.js';
import { assetDownloadUrl, needsFormatChoice, type DownloadFormat } from './download-format.js';

/** A download parked until the user says what its RAW and HEIC/HEIF travel as. */
export interface FormatChoice {
	assetIds: string[];
	options: DownloadOptionsResponse;
}

/**
 * Downloads assets, stopping first at "original or JPEG?" when the server says
 * the selection holds a RAW or a HEIC/HEIF (see DownloadFormatDialog). One
 * asset downloads as its file; several as a ZIP.
 *
 * There is no "remember my answer": the right one depends on where the photos
 * are going, not on who is asking.
 */
export class AssetDownloader {
	busy = $state(false);
	/** Non-null while the format question is up. */
	choice = $state<FormatChoice | null>(null);

	async start(ids: readonly string[]) {
		if (this.busy || ids.length === 0) return;
		const assetIds = [...ids];
		this.busy = true;
		try {
			const options = await downloadOptions(assetIds);
			if (needsFormatChoice(options)) this.choice = { assetIds, options };
			else await downloadAssets(assetIds, null);
		} catch {
			toasts.error(m.action_failed());
		} finally {
			this.busy = false;
		}
	}

	async choose(format: DownloadFormat) {
		const choice = this.choice;
		if (!choice || this.busy) return;
		this.choice = null;
		this.busy = true;
		try {
			await downloadAssets(choice.assetIds, format);
		} catch {
			toasts.error(m.action_failed());
		} finally {
			this.busy = false;
		}
	}

	cancel() {
		this.choice = null;
	}
}

/**
 * Whether there is a format to ask about. A server that can't say (an older
 * one, an error) gets no question: the download goes on as it always did.
 */
async function downloadOptions(assetIds: string[]) {
	try {
		const { data } = await getAssetsDownloadOptions({ body: { assetIds } });
		return data ?? null;
	} catch {
		return null;
	}
}

/** Saves the files; `format` null sends what the server sends by default (the original). */
export async function downloadAssets(assetIds: readonly string[], format: DownloadFormat | null) {
	if (assetIds.length === 1) {
		// Its own file, with the name the server gives it; the media cookie
		// authenticates a plain link, so the browser streams it to disk.
		clickDownload(assetDownloadUrl(assetIds[0], format));
		return;
	}
	toasts.show(m.action_zip_preparing());
	const { data, error } = await downloadAssetsZip({
		body: { assetIds: [...assetIds], format },
		parseAs: 'blob'
	});
	if (error || !(data instanceof Blob)) throw error ?? new Error('Empty ZIP');
	const url = URL.createObjectURL(data);
	clickDownload(url, `photonne-${fileStamp()}.zip`);
	setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function clickDownload(href: string, fileName = '') {
	Object.assign(document.createElement('a'), { href, download: fileName }).click();
}
