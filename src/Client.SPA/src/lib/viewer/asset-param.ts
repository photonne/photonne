/** `href` (path + query) with `?asset=` set to `assetId`, or removed for null. */
export function withAssetParam(href: string, assetId: string | null) {
	const url = new URL(href);
	if (assetId) url.searchParams.set('asset', assetId);
	else url.searchParams.delete('asset');
	return url.pathname + url.search;
}
