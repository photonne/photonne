import { checkChecksums, type UploadAssetResponse } from '#lib/api/index.js';
import { session } from '#lib/auth/session.svelte.js';
import {
	appendDates,
	failureOf,
	isDuplicateMessage,
	postForm,
	UploadError,
	type UploadTransport
} from './transport.js';

/**
 * Uploads into the signed-in user's library (`/api/assets/upload`, which
 * files them under their Uploads folder). An expired access token gets one
 * refresh and a retry, as the generated client does for every other call.
 */
export const libraryTransport: UploadTransport = {
	async precheck(checksums) {
		const { data } = await checkChecksums({ body: { checksums } });
		return data?.existing ?? {};
	},

	async upload(file, options) {
		const send = (token: string | null) => {
			const form = new FormData();
			form.append('file', file, file.name);
			appendDates(form, file);
			return postForm('/api/assets/upload', form, {
				...options,
				headers: token ? { Authorization: `Bearer ${token}` } : {}
			});
		};

		let response = await send(session.accessToken);
		if (response.status === 401) {
			const token = await session.refresh();
			if (token) response = await send(token);
		}
		if (response.status >= 200 && response.status < 300) {
			const body = response.body as UploadAssetResponse;
			return { assetId: body.assetId, duplicate: isDuplicateMessage(body) };
		}
		throw new UploadError(response.status === 401 ? 'server' : failureOf(response));
	}
};
