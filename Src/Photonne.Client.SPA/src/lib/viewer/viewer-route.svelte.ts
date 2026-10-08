import { goto } from '$app/navigation';
import { page } from '$app/state';
import { withAssetParam } from './asset-param.js';

/**
 * The viewer's place in the URL (?asset=…): Back closes it, a link opens it,
 * and the page underneath stays mounted with its scroll intact. Shared by
 * every page with a photo grid.
 */
export class ViewerRoute {
	openId = $derived(page.url.searchParams.get('asset'));
	#openedHere = false;

	#url(assetId: string | null) {
		return withAssetParam(page.url.href, assetId);
	}

	open(assetId: string) {
		this.#openedHere = true;
		return goto(this.#url(assetId), { reset: false });
	}

	navigate(assetId: string) {
		return goto(this.#url(assetId), { replace: true, reset: false });
	}

	/** Closes and returns the id that was shown, to put focus back on it. */
	async close() {
		const last = this.openId;
		if (this.#openedHere) history.back();
		else await goto(this.#url(null), { replace: true, reset: false });
		this.#openedHere = false;
		return last;
	}
}
