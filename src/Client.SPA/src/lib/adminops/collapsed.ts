import { sectionIds, type SectionId } from './catalog.js';

const KEY = 'photonne.admin.maintenance.collapsed';

/**
 * The maintenance groups the admin folded. Unknown names are dropped rather
 * than failing the read: groups get renamed, and a stale preference should
 * cost a fold, not the page.
 */
export function parseCollapsed(raw: string | null): SectionId[] {
	if (!raw) return [];
	return raw
		.split(',')
		.map((name) => name.trim())
		.filter((name): name is SectionId => (sectionIds as string[]).includes(name));
}

export function readCollapsed(): SectionId[] {
	try {
		return parseCollapsed(localStorage.getItem(KEY));
	} catch {
		return [];
	}
}

export function writeCollapsed(sections: readonly SectionId[]) {
	try {
		localStorage.setItem(KEY, sections.join(','));
	} catch {
		// Private mode or blocked storage: the fold just isn't remembered.
	}
}
