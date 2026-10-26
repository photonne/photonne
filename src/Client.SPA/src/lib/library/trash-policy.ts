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

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Whole days until the nightly cleanup purges an item trashed at `deletedAt`
 * (it goes once it is older than the retention), rounded up; 0 means the next
 * cleanup takes it. Null without a retention or a deletion date.
 */
export function daysLeft(
	deletedAt: string | null | undefined,
	retentionDays: number | null,
	now = Date.now()
): number | null {
	if (!deletedAt || !retentionDays) return null;
	const deleted = Date.parse(deletedAt);
	if (Number.isNaN(deleted)) return null;
	return Math.max(0, Math.ceil((deleted + retentionDays * DAY_MS - now) / DAY_MS));
}
