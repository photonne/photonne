import { describe, expect, it } from 'vitest';
import {
	changes,
	normalizeDecimal,
	validate,
	validateField,
	withDefaults,
	type FieldDef
} from './settings-model.js';

const label = () => '';
const int: FieldDef = { key: 'n', kind: 'int', default: '5', label, min: 1, max: 10 };
const decimal: FieldDef = { key: 'd', kind: 'decimal', default: '0.5', label, min: 0, max: 1 };
const url: FieldDef = { key: 'u', kind: 'url', default: '', label };
const time: FieldDef = { key: 't', kind: 'time', default: '02:00', label };
const bool: FieldDef = { key: 'b', kind: 'bool', default: 'true', label };
const text: FieldDef = { key: 's', kind: 'text', default: 'x', label, required: true };

describe('settings model', () => {
	it('validates numbers against their range', () => {
		expect(validateField(int, '7')).toBeNull();
		expect(validateField(int, '')).toBe('required');
		expect(validateField(int, '7.5')).toBe('notNumber');
		expect(validateField(int, '11')).toBe('range');
		expect(validateField(decimal, '0,42')).toBeNull();
		expect(validateField(decimal, '1.2')).toBe('range');
		expect(validateField(decimal, 'abc')).toBe('notNumber');
	});

	it('validates urls, times and required text', () => {
		expect(validateField(url, '')).toBeNull();
		expect(validateField(url, 'https://photos.example.com')).toBeNull();
		expect(validateField(url, 'ftp://x')).toBe('url');
		expect(validateField(url, 'not a url')).toBe('url');
		expect(validateField(time, '23:59')).toBeNull();
		expect(validateField(time, '24:00')).toBe('time');
		expect(validateField(text, ' ')).toBe('required');
	});

	it('adds the keys a cross-field rule blames', () => {
		expect(validate([int, decimal], { n: '3', d: '0.4' }, () => ['d'])).toEqual({ d: 'cross' });
		expect(validate([int], { n: '0' }, () => ['n'])).toEqual({ n: 'range' });
	});

	it('fills keys never stored with the server default', () => {
		expect(withDefaults([int, bool], { n: '', b: 'false' })).toEqual({ n: '5', b: 'false' });
	});

	it('saves only what changed, spelled the way the server wants', () => {
		const original = { n: '5', d: '0.5', b: 'True' };
		expect(changes([int, decimal, bool], original, { n: '5', d: '0,5', b: 'true' })).toEqual([]);
		expect(changes([int, decimal, bool], original, { n: ' 6 ', d: '0,6', b: 'false' })).toEqual([
			['n', '6'],
			['d', '0.6'],
			['b', 'false']
		]);
	});

	it('normalises the decimal comma', () => {
		expect(normalizeDecimal(' 0,25 ')).toBe('0.25');
	});
});
