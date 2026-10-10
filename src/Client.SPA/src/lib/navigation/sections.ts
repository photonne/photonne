import type { IconName } from '#lib/components/Icon.svelte';
import { m } from '#lib/paraglide/messages.js';

export interface NavItem {
	path: string;
	label: () => string;
	icon: IconName;
}

export interface NavSection {
	id: string;
	/** Heading over the items; the first section, the photos, has none. */
	label?: () => string;
	items: NavItem[];
	adminOnly?: boolean;
}

/**
 * The sidebar, in order. Paths are app paths (resolved by AppShell). The
 * same grouping as the native app: Fotos, Colecciones (its default order),
 * Fijados (drawn by AppShell after Colecciones), the actions of «Más» and
 * the administration.
 */
export const navigation: NavSection[] = [
	{
		id: 'photos',
		items: [{ path: '/', label: m.nav_photos, icon: 'photos' }]
	},
	{
		id: 'collections',
		label: m.nav_section_collections,
		items: [
			{ path: '/memories', label: m.nav_memories, icon: 'history' },
			{ path: '/people', label: m.nav_people, icon: 'people' },
			{ path: '/favorites', label: m.nav_favorites, icon: 'favoriteOutline' },
			{ path: '/albums', label: m.nav_albums, icon: 'album' },
			{ path: '/folders', label: m.nav_folders, icon: 'folder' },
			{ path: '/explore', label: m.nav_explore, icon: 'explore' },
			{ path: '/map', label: m.nav_map, icon: 'place' },
			{ path: '/archive', label: m.nav_archive, icon: 'archive' },
			{ path: '/trash', label: m.nav_trash, icon: 'delete' }
		]
	},
	{
		id: 'actions',
		label: m.nav_section_actions,
		items: [
			{ path: '/upload', label: m.nav_upload, icon: 'upload' },
			{ path: '/organize', label: m.nav_organize, icon: 'inbox' },
			{ path: '/links', label: m.links_nav, icon: 'link' },
			{ path: '/utilities', label: m.nav_utilities, icon: 'build' }
		]
	},
	{
		id: 'admin',
		label: m.nav_admin,
		adminOnly: true,
		items: [{ path: '/admin', label: m.nav_admin, icon: 'shield' }]
	}
];
