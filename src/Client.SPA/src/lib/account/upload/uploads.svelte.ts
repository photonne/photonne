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
