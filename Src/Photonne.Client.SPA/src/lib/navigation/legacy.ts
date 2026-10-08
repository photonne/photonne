/**
 * Where an address of the previous web client (Blazor) lives now, or null
 * when it isn't one of them. Old bookmarks and notification links keep
 * working; the query string is kept by the caller.
 */
const EXACT: Record<string, string> = {
	'/fotos': '/',
	'/albumes': '/albums',
	'/carpetas': '/folders',
	'/archivadas': '/archive',
	'/favoritas': '/favorites',
	'/papelera': '/trash',
	'/buscar': '/search',
	'/mapa': '/map',
	'/organizar': '/organize',
	'/duplicados': '/utilities/duplicates',
	'/shared-trash': '/trash?scope=shared',
	'/use-the-app': '/',
	'/not-found': '/',
	'/admin/stats': '/admin',
	'/admin/system/backup': '/admin/backup',
	'/admin/system/maintenance': '/admin/maintenance',
	'/admin/system/tasks/duplicates': '/admin/maintenance/duplicates',
	'/admin/enrichment-failures': '/admin/tasks/failures',
	'/admin/settings/face-recognition': '/admin/settings/faces',
	'/admin/settings/object-detection': '/admin/settings/objects',
	'/admin/settings/scene-classification': '/admin/settings/scenes',
	'/admin/settings/text-recognition': '/admin/settings/text',
	'/admin/settings/image-embedding': '/admin/settings/embedding',
	'/admin/settings/user-defaults': '/admin/settings/users',
	'/admin/settings/tasks': '/admin/settings/performance',
	'/admin/settings/version': '/admin/system'
};

const PREFIXES: [RegExp, string][] = [
	[/^\/albumes\/([^/]+)$/, '/albums/$1'],
	[/^\/carpetas\/([^/]+)$/, '/folders/$1'],
	// The per-task pages became one tasks page.
	[/^\/admin\/system\/tasks(\/[^/]+)?$/, '/admin/tasks']
];

export function legacyPath(pathname: string): string | null {
	const path = pathname.length > 1 ? pathname.replace(/\/$/, '') : pathname;
	if (path in EXACT) return EXACT[path];
	for (const [pattern, target] of PREFIXES) {
		if (pattern.test(path)) return path.replace(pattern, target);
	}
	return null;
}

/** `legacyPath` plus the original query, merged into the target's own. */
export function legacyTarget(url: URL): string | null {
	const target = legacyPath(url.pathname);
	if (target === null) return null;
	if (!url.search) return target + url.hash;
	const joiner = target.includes('?') ? '&' : '?';
	return target + joiner + url.search.slice(1) + url.hash;
}
