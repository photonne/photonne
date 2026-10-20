import type { IconName } from '#lib/components/Icon.svelte';
import { m } from '#lib/paraglide/messages.js';
import { navigation } from './sections.js';

export type TabId = 'photos' | 'collections' | 'more';

export interface Tab {
	id: TabId;
	path: string;
	label: () => string;
	icon: IconName;
	/** The icon of the current tab, as in the native app (filled). */
	activeIcon: IconName;
}

/** The phone's navigation, as in the native app: three tabs. */
export const tabs: Tab[] = [
	{ id: 'photos', path: '/', label: m.nav_photos, icon: 'photosOutline', activeIcon: 'photos' },
	{
		id: 'collections',
		path: '/collections',
		label: m.nav_section_collections,
		icon: 'collectionsOutline',
		activeIcon: 'collections'
	},
	{ id: 'more', path: '/more', label: m.nav_more, icon: 'gridViewOutline', activeIcon: 'gridView' }
];

const collectionPaths = navigation.find((section) => section.id === 'collections')!.items;

function under(pathname: string, base: string) {
	const root = base.replace(/\/$/, '');
	return pathname === base || pathname.startsWith(`${root}/`);
}

/**
 * The tab a page belongs to: the photos, a collection (or a pinned album or
 * folder, which live under the albums and the folders) or anything else,
 * which «Más» leads to. `href` turns an app path into the page's pathname.
 */
export function tabFor(pathname: string, href: (path: string) => string): TabId {
	if (pathname === href('/')) return 'photos';
	const inCollections = ['/collections', ...collectionPaths.map((item) => item.path)];
	if (inCollections.some((path) => under(pathname, href(path)))) return 'collections';
	return 'more';
}
