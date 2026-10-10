import { describe, expect, it } from 'vitest';
import { trashPolicy } from './trash-policy.js';

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
