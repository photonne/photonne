/**
 * Automatic scan schedules. The server only understands these four intervals
 * (as `@name` or their plain cron form), so the form offers them as choices
 * instead of a free cron field.
 */
export const SCHEDULES = ['hourly', 'daily', 'weekly', 'monthly'] as const;
export type Schedule = (typeof SCHEDULES)[number];

const CRON_FORMS: Record<Schedule, string> = {
	hourly: '0 * * * *',
	daily: '0 0 * * *',
	weekly: '0 0 * * 0',
	monthly: '0 0 1 * *'
};

/**
 * The schedule a stored cron expression means: 'manual' when there is none,
 * 'unknown' for an expression the server would not run (kept as is).
 */
export function scheduleOf(cron: string | null | undefined): Schedule | 'manual' | 'unknown' {
	const expression = cron?.trim().toLowerCase();
	if (!expression) return 'manual';
	for (const schedule of SCHEDULES) {
		if (expression === `@${schedule}` || expression === CRON_FORMS[schedule]) return schedule;
	}
	return 'unknown';
}

/** The cron expression saved for a schedule; null for manual scans only. */
export function cronOf(schedule: Schedule | 'manual'): string | null {
	return schedule === 'manual' ? null : `@${schedule}`;
}

/** A scan's progress as the page shows it, from the stream's updates. */
export interface ScanState {
	libraryId: string;
	taskId: string | null;
	message: string;
	percentage: number;
	found: number;
	indexed: number;
	missing: number;
	done: boolean;
	error: string | null;
	/** Re-attached to a scan started earlier (another tab, before a reload). */
	resumed: boolean;
	/** The admin asked to stop it. */
	cancelled: boolean;
}

export interface ScanUpdate {
	message?: string;
	percentage?: number;
	assetsFound?: number;
	assetsIndexed?: number;
	assetsMarkedMissing?: number;
	isCompleted?: boolean;
	error?: string | null;
	taskId?: string | null;
}

export function startScan(libraryId: string, resumed = false, percentage = 0): ScanState {
	return {
		libraryId,
		taskId: null,
		message: '',
		percentage,
		found: 0,
		indexed: 0,
		missing: 0,
		done: false,
		error: null,
		resumed,
		cancelled: false
	};
}

/** Folds one streamed update into the scan state. */
export function applyScanUpdate(state: ScanState, update: ScanUpdate): ScanState {
	return {
		...state,
		taskId: update.taskId ?? state.taskId,
		message: update.message ?? state.message,
		percentage: Math.max(0, Math.min(100, update.percentage ?? state.percentage)),
		found: update.assetsFound ?? state.found,
		indexed: update.assetsIndexed ?? state.indexed,
		missing: update.assetsMarkedMissing ?? state.missing,
		done: state.done || !!update.isCompleted,
		error: update.error ?? state.error
	};
}
