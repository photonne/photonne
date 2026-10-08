import { redirect } from '@sveltejs/kit';
import { legacyTarget } from '#lib/navigation/legacy.js';
import { appHref } from '#lib/navigation/href.js';

// Everything renders in the browser: the app lives behind a login, and the
// server only hands out the static build (see vite.config.ts).
export const ssr = false;
export const prerender = false;

// Addresses of the previous web client (bookmarks, links in old
// notifications) land on their page here.
export function load({ url }) {
	const target = legacyTarget(url);
	if (target) redirect(308, appHref(target));
}
