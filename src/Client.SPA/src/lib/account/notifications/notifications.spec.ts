import { describe, expect, it } from 'vitest';
import { notificationIcon, notificationTarget, relativeTime } from './notifications.js';

describe('notificationTarget', () => {
	it.each([
		['/albums/abc', '/albums/abc'],
		['/admin/enrichment-failures?type=Exif', '/admin/tasks/failures?type=Exif'],
		['/admin/stats', '/admin'],
		['/shared-trash', '/trash?scope=shared'],
		['/shared-trash?x=1', '/trash?scope=shared&x=1']
	])('%s → %s', (url, target) => {
		expect(notificationTarget(url)).toBe(target);
	});

	it.each([null, undefined, '', 'albums/1', '//evil.example', '/\\evil', 'https://evil.example'])(
		'ignores %s',
		(url) => {
			expect(notificationTarget(url)).toBeNull();
		}
	);
});

describe('relativeTime', () => {
	const now = Date.parse('2026-10-08T12:00:00Z');

	it('says "now" for the last minute', () => {
		expect(relativeTime('2026-10-08T11:59:30Z', 'es', now)).toBe('ahora');
	});

	it('counts minutes, hours and days', () => {
		expect(relativeTime('2026-10-08T11:55:00Z', 'en', now)).toBe('5 minutes ago');
		expect(relativeTime('2026-10-08T09:00:00Z', 'en', now)).toBe('3 hours ago');
		expect(relativeTime('2026-10-07T06:00:00Z', 'es', now)).toBe('ayer');
	});

	it('gives a date after a week', () => {
		expect(relativeTime('2026-09-01T12:00:00Z', 'en', now)).toBe('Sep 1, 2026');
	});
});

describe('notificationIcon', () => {
	it('marks failed jobs as errors', () => {
		expect(notificationIcon(2).tone).toBe('danger');
		expect(notificationIcon(99).tone).toBe('muted');
	});
});
