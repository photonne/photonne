import {
	appendDates,
	failureOf,
	isDuplicateMessage,
	postForm,
	UploadError,
	type UploadTransport
} from '../upload/transport.js';

/**
 * Guest uploads through a photo-request link (`/api/share/{token}/upload`):
 * no session, the link's password in the form, and an optional name the
 * owner files the photos under. There is no checksum lookup for guests; the
 * server still drops exact duplicates on arrival.
 */
export function shareTransport(
	guest: () => { token: string; password: string | null; name: string }
): UploadTransport {
	return {
		async upload(file, options) {
			const { token, password, name } = guest();
			const form = new FormData();
			form.append('file', file, file.name);
			if (name.trim()) form.append('uploaderName', name.trim());
			if (password) form.append('pw', password);
			appendDates(form, file);
			const response = await postForm(
				`/api/share/${encodeURIComponent(token)}/upload`,
				form,
				options
			);
			if (response.status >= 200 && response.status < 300) {
				return { assetId: null, duplicate: isDuplicateMessage(response.body) };
			}
			throw new UploadError(failureOf(response));
		}
	};
}
