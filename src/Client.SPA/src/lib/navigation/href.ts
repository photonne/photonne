import { resolve } from '$app/paths';

/**
 * href for an app path such as `/albums/123`. Typed routes can't check a
 * path assembled from data, so this is the one place that casts; it still
 * goes through `resolve`, so a base path is honoured.
 */
export function appHref(path: string): string {
	return (resolve as (pathname: string) => string)(path.replace(/^\//, ''));
}
