/**
 * The server's trash rules, read from global settings any signed-in user may
 * read (the same keys the admin trash settings write).
 */
export const TRASH_SETTING_KEYS = {
	enabled: 'TrashSettings.Enabled',
	retentionDays: 'TrashSettings.RetentionDays',
	maxQuotaMb: 'TrashSettings.MaxQuotaMb',
	cleanup: 'NightlyTaskSettings.TrashCleanup.Enabled'
} as const;

export interface TrashPolicy {
	/** Off: "move to trash" deletes for good, so nothing can be undone. */
	enabled: boolean;
	/** Days an item stays before the nightly cleanup purges it; null: kept until emptied. */
	retentionDays: number | null;
	/** Per-user size above which the oldest items go first; null: no limit. */
	maxQuotaMb: number | null;
}

type Raw = Partial<Record<keyof typeof TRASH_SETTING_KEYS, string | undefined>>;

/**
 * Builds the policy from raw setting values with the server's own defaults
 * (trash on, 30 days, no quota, nightly cleanup off). Retention and quota
 * only apply when the nightly cleanup runs.
 */
export function trashPolicy(raw: Raw): TrashPolicy {
	const enabled = (raw.enabled?.trim() || 'true').toLowerCase() === 'true';
	const cleanup = (raw.cleanup?.trim() || 'false').toLowerCase() === 'true';
	const days = positiveInt(raw.retentionDays, 30);
	const quota = positiveInt(raw.maxQuotaMb, 0);
	return {
		enabled,
		retentionDays: enabled && cleanup && days > 0 ? days : null,
		maxQuotaMb: enabled && cleanup && quota > 0 ? quota : null
	};
}

function positiveInt(value: string | undefined, fallback: number) {
	if (!value?.trim()) return fallback;
	const parsed = Number.parseInt(value, 10);
	return Number.isFinite(parsed) ? Math.max(0, parsed) : fallback;
}
