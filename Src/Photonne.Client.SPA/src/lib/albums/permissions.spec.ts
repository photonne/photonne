import { describe, expect, it } from 'vitest';
import { orderItems } from './item-order.js';
import { grantsFor, levelOf } from './permissions.js';

describe('access levels', () => {
	it('builds each level on the previous one', () => {
		expect(grantsFor('view')).toEqual({
			canRead: true,
			canWrite: false,
			canDelete: false,
			canManagePermissions: false
		});
		expect(grantsFor('manage')).toEqual({
			canRead: true,
			canWrite: true,
			canDelete: true,
			canManagePermissions: true
		});
	});

	it('reads grants back as a level, or custom', () => {
		expect(levelOf(grantsFor('contribute'))).toBe('contribute');
		expect(
			levelOf({ canRead: true, canWrite: false, canDelete: true, canManagePermissions: false })
		).toBe('custom');
	});
});

describe('orderItems', () => {
	const items = [
		{ id: 'a', capturedAt: '2024-05-01T00:00:00Z' },
		{ id: 'b', capturedAt: '2026-01-01T00:00:00Z' },
		{ id: 'c', capturedAt: '2024-05-01T00:00:00Z' }
	];

	it('keeps the album order', () => {
		expect(orderItems(items, 'album', (i) => i.capturedAt).map((i) => i.id)).toEqual([
			'a',
			'b',
			'c'
		]);
	});

	it('sorts by capture date, ties in album order', () => {
		expect(orderItems(items, 'newest', (i) => i.capturedAt).map((i) => i.id)).toEqual([
			'b',
			'a',
			'c'
		]);
		expect(orderItems(items, 'oldest', (i) => i.capturedAt).map((i) => i.id)).toEqual([
			'a',
			'c',
			'b'
		]);
	});
});
