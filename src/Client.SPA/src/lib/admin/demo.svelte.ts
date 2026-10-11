import { createQuery } from '@tanstack/svelte-query';
import { getDemoInfoOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';

/**
 * Whether the server runs as the public demo, where it refuses the admin's
 * destructive actions (backups, external libraries, admin accounts, server
 * settings). Pages read it to disable those actions up front instead of
 * letting the server say no. Every caller shares the same cached query.
 *
 * Call it while a component initialises (it subscribes to the query).
 */
export function useDemoMode() {
	const query = createQuery(() => getDemoInfoOptions());
	return {
		get readOnly() {
			return query.data?.enabled === true;
		}
	};
}
