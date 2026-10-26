import { describe, expect, it } from 'vitest';
import { daysLeft, trashPolicy } from './trash-policy.js';

describe('trashPolicy', () => {
	it("uses the server's defaults when nothing is set", () => {
		expect(trashPolicy({})).toEqual({ enabled: true, retentionDays: null, maxQuotaMb: null });
		expect(trashPolicy({ enabled: '', retentionDays: '', cleanup: '' })).toEqual({
			enabled: true,
			retentionDays: null,
			maxQuotaMb: null
		});
	});

	it('applies retention and quota only when the nightly cleanup runs', () => {
		expect(trashPolicy({ cleanup: 'true' })).toEqual({
			enabled: true,
			retentionDays: 30,
			maxQuotaMb: null
		});
		expect(trashPolicy({ cleanup: 'True', retentionDays: '7', maxQuotaMb: '2048' })).toEqual({
			enabled: true,
			retentionDays: 7,
			maxQuotaMb: 2048
		});
		expect(trashPolicy({ cleanup: 'false', retentionDays: '7' }).retentionDays).toBeNull();
	});

	it('treats 0 days as keeping until emptied', () => {
		expect(trashPolicy({ cleanup: 'true', retentionDays: '0' }).retentionDays).toBeNull();
	});

	it('falls back on unreadable numbers', () => {
		expect(trashPolicy({ cleanup: 'true', retentionDays: 'soon' }).retentionDays).toBe(30);
	});

	it('knows a server without trash', () => {
		expect(trashPolicy({ enabled: 'false', cleanup: 'true' })).toEqual({
			enabled: false,
			retentionDays: null,
			maxQuotaMb: null
		});
	});
});

describe('daysLeft', () => {
	const now = Date.parse('2026-10-26T12:00:00Z');

	it('counts whole days left of the retention, rounding up', () => {
		expect(daysLeft('2026-10-26T11:00:00Z', 30, now)).toBe(30);
		expect(daysLeft('2026-10-20T12:00:00Z', 30, now)).toBe(24);
		expect(daysLeft('2026-09-26T13:00:00Z', 30, now)).toBe(1);
	});

	it('is 0 once the retention has passed', () => {
		expect(daysLeft('2026-09-01T00:00:00Z', 30, now)).toBe(0);
	});

	it('is null without a retention or a date', () => {
		expect(daysLeft('2026-10-20T12:00:00Z', null, now)).toBeNull();
		expect(daysLeft(null, 30, now)).toBeNull();
		expect(daysLeft('nonsense', 30, now)).toBeNull();
	});
});
