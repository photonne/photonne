import { redirect } from '@sveltejs/kit';
import { appHref } from '#lib/navigation/href.js';

// /settings opens on the first section; each section has its own URL.
export function load() {
	redirect(307, appHref('/settings/profile'));
}
