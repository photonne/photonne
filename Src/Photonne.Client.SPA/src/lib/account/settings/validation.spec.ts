import { describe, expect, it } from 'vitest';
import { isStrongPassword, isValidEmail, isValidUsername, passwordRules } from './validation.js';

describe('passwordRules', () => {
	it('reports each missing rule', () => {
		expect(passwordRules('abc')).toEqual({
			length: false,
			upper: false,
			lower: true,
			digit: false,
			symbol: false
		});
		expect(passwordRules('Secreto-2026')).toEqual({
			length: true,
			upper: true,
			lower: true,
			digit: true,
			symbol: true
		});
	});

	it('accepts non-ASCII letters and treats spaces as symbols', () => {
		expect(isStrongPassword('Ñandú añejo 9')).toBe(true);
		expect(isStrongPassword('x'.repeat(129) + 'A1!')).toBe(false);
	});
});

describe('isValidUsername', () => {
	it.each([
		['ana', true],
		['ana.garcia_2-b', true],
		['', false],
		['ana garcía', false],
		['a'.repeat(65), false]
	])('%s → %s', (name, valid) => {
		expect(isValidUsername(name)).toBe(valid);
	});
});

describe('isValidEmail', () => {
	it('catches obvious typos', () => {
		expect(isValidEmail('ana@photonne.test')).toBe(true);
		expect(isValidEmail('ana@photonne')).toBe(false);
		expect(isValidEmail('ana photonne.test')).toBe(false);
	});
});
