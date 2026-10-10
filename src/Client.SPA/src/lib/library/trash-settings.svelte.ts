import { createQuery } from '@tanstack/svelte-query';
import { getSettingOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { TRASH_SETTING_KEYS, trashPolicy } from './trash-policy.js';

/**
 * The server's trash policy, live: four global settings (see trash-policy.ts).
 * Until they arrive (or if they fail) it assumes the server's defaults.
 */
export function useTrashPolicy() {
	const queries = {
		enabled: createQuery(() => getSettingOptions({ query: { key: TRASH_SETTING_KEYS.enabled } })),
		retentionDays: createQuery(() =>
			getSettingOptions({ query: { key: TRASH_SETTING_KEYS.retentionDays } })
		),
		maxQuotaMb: createQuery(() =>
			getSettingOptions({ query: { key: TRASH_SETTING_KEYS.maxQuotaMb } })
		),
		cleanup: createQuery(() => getSettingOptions({ query: { key: TRASH_SETTING_KEYS.cleanup } }))
	};
	const policy = $derived(
		trashPolicy({
			enabled: queries.enabled.data?.value,
			retentionDays: queries.retentionDays.data?.value,
			maxQuotaMb: queries.maxQuotaMb.data?.value,
			cleanup: queries.cleanup.data?.value
		})
	);
	return {
		get current() {
			return policy;
		},
		get ready() {
			return Object.values(queries).every((query) => !query.isPending);
		}
	};
}
