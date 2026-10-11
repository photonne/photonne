import { describe, expect, it } from 'vitest';
import { demoRefusal, errorText } from './errors.js';

const demo = {
	type: 'https://tools.ietf.org/html/rfc9110#section-15.5.4',
	title: 'Acción deshabilitada en la demo',
	status: 403,
	detail: 'Esta acción está bloqueada en la demo pública de Photonne.',
	demoMode: true
};

describe('demoRefusal', () => {
	it('reads the detail of the demo ProblemDetails', () => {
		expect(demoRefusal(demo)).toBe(demo.detail);
	});

	it('ignores other errors', () => {
		expect(demoRefusal({ error: 'x', code: 'user_not_found' })).toBeNull();
		expect(demoRefusal({ ...demo, demoMode: undefined })).toBeNull();
		expect(demoRefusal('boom')).toBeNull();
		expect(demoRefusal(undefined)).toBeNull();
	});
});

describe('errorText', () => {
	it('shows the demo explanation instead of the generic fallback', () => {
		expect(errorText(demo, () => 'fallback')).toBe(demo.detail);
	});

	it('falls back for an unknown error', () => {
		expect(errorText({ status: 500 }, () => 'fallback')).toBe('fallback');
	});
});
