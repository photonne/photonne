import type { NotificationItemResponse } from '#lib/api/index.js';
import { ICON_DELETE_SWEEP, ICON_ERROR, ICON_TASK_DONE, ICON_VISIBILITY } from '../icons.js';

/** NotificationType on the server (Shared/Models/Notification.cs). */
export const NotificationKind = {
	JobCompleted: 1,
	JobFailed: 2,
	ShareViewed: 3,
	SharedAssetsDeleted: 4,
	ShareUploaded: 5
} as const;

const UPLOAD_PATH = 'M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z';

export function notificationIcon(type: NotificationItemResponse['type']): {
	path: string;
	tone: 'accent' | 'danger' | 'muted';
} {
	switch (type) {
		case NotificationKind.JobCompleted:
			return { path: ICON_TASK_DONE, tone: 'accent' };
		case NotificationKind.JobFailed:
			return { path: ICON_ERROR, tone: 'danger' };
		case NotificationKind.ShareViewed:
			return { path: ICON_VISIBILITY, tone: 'accent' };
		case NotificationKind.SharedAssetsDeleted:
			return { path: ICON_DELETE_SWEEP, tone: 'muted' };
		case NotificationKind.ShareUploaded:
			return { path: UPLOAD_PATH, tone: 'accent' };
		default:
			return { path: ICON_TASK_DONE, tone: 'muted' };
	}
}

// Action URLs are written by the server for the Blazor client; the few whose
// page lives elsewhere in this app are mapped here. The shared trash
// notification also reaches folder managers who aren't admins, so it leads
// to their own view of it.
const MOVED: Record<string, string> = {
	'/shared-trash': '/trash?scope=shared',
	'/admin/enrichment-failures': '/admin/tasks/failures',
	'/admin/stats': '/admin'
};

/**
 * The app path a notification leads to, or null when it has none (or it
 * points outside the app: never follow those from a notification).
 */
export function notificationTarget(actionUrl: string | null | undefined): string | null {
	if (!actionUrl || !actionUrl.startsWith('/') || actionUrl.startsWith('//')) return null;
	if (actionUrl.startsWith('/\\')) return null;
	const [path, rest = ''] = actionUrl.split(/(?=[?#])/, 2);
	const target = MOVED[path] ?? path;
	return target.includes('?') && rest.startsWith('?')
		? `${target}&${rest.slice(1)}`
		: target + rest;
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
	['second', 60],
	['minute', 60],
	['hour', 24],
	['day', 7]
];

/**
 * "hace 5 minutos", "ayer"… for the last week; older ones get a date. The
 * instant is a real UTC time (not a camera's wall clock), so it is shown in
 * the user's own time zone.
 */
export function relativeTime(iso: string, locale: string, now = Date.now()): string {
	const then = Date.parse(iso);
	let value = (then - now) / 1000;
	for (const [unit, size] of UNITS) {
		if (Math.abs(value) < size) {
			if (unit === 'second') {
				return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(0, 'second');
			}
			return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(
				Math.trunc(value),
				unit
			);
		}
		value /= size;
	}
	return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(new Date(then));
}
