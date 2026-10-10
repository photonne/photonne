import { redirect } from '@sveltejs/kit';
import { appHref } from '#lib/navigation/href.js';

// The settings open on their first section; the side navigation does the rest.
export function load() {
	redirect(307, appHref('/admin/settings/server'));
}
