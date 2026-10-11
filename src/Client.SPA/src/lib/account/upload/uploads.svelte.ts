import { queryClient } from '#lib/api/query-client.js';
import { session } from '#lib/auth/session.svelte.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';
import { libraryTransport } from './library-transport.js';
import { UploadQueue } from './upload-queue.svelte.js';

/**
 * The app's one upload queue into the user's library. It outlives the upload
 * page, so files keep going while the user browses, and any drop zone
 * (the upload page, or an app-wide one) feeds the same queue.
 */
export const uploads = new UploadQueue(libraryTransport);

// Closing the tab would silently drop what is still going up.
if (typeof window !== 'undefined') {
	window.addEventListener('beforeunload', (event) => {
		if (uploads.active) event.preventDefault();
	});
}

// New photos change the timeline, months, folders, storage…: everything
// cached is stale once a batch lands, whichever page the files were dropped on.
uploads.ondrained((assetIds) => {
	queryClient.invalidateQueries();
	toasts.show(m.upload_batch_done({ count: assetIds.length }));
});

// Files still waiting were the last user's: they must not go up with the
// next one's session.
session.onsignout(() => uploads.cancelAll());
