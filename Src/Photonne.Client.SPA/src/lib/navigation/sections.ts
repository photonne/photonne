import type { IconName } from '#lib/components/Icon.svelte';
import { m } from '#lib/paraglide/messages.js';

export interface NavItem {
	path: string;
	label: () => string;
	icon: IconName;
}

export interface NavSection {
	label: () => string;
	items: NavItem[];
	adminOnly?: boolean;
}

/** The sidebar, in order. Paths are app paths (resolved by AppShell). */
export const navigation: NavSection[] = [
	{
		label: m.nav_section_library,
		items: [
			{ path: '/', label: m.nav_photos, icon: 'photos' },
			{ path: '/explore', label: m.nav_explore, icon: 'explore' },
			{ path: '/people', label: m.nav_people, icon: 'people' },
			{ path: '/map', label: m.nav_map, icon: 'place' },
			{ path: '/memories', label: m.nav_memories, icon: 'history' }
		]
	},
	{
		label: m.nav_section_collections,
		items: [
			{ path: '/albums', label: m.nav_albums, icon: 'album' },
			{ path: '/folders', label: m.nav_folders, icon: 'folder' },
			{ path: '/favorites', label: m.nav_favorites, icon: 'favoriteOutline' },
			{ path: '/archive', label: m.nav_archive, icon: 'archive' },
			{ path: '/trash', label: m.nav_trash, icon: 'delete' }
		]
	},
	{
		label: m.nav_section_manage,
		items: [
			{ path: '/upload', label: m.nav_upload, icon: 'upload' },
			{ path: '/organize', label: m.nav_organize, icon: 'inbox' },
			{ path: '/utilities', label: m.nav_utilities, icon: 'build' }
		]
	},
	{
		label: m.nav_admin,
		adminOnly: true,
		items: [{ path: '/admin', label: m.nav_admin, icon: 'shield' }]
	}
];
