import { describe, expect, it } from 'vitest';
import { m } from '#lib/paraglide/messages.js';

describe('counts in messages', () => {
	it('group their thousands in the language of the message', () => {
		expect(m.selection_count({ count: 12345 }, { locale: 'es' })).toBe('12.345 seleccionados');
		expect(m.selection_count({ count: 12345 }, { locale: 'en' })).toBe('12,345 selected');
		expect(m.selection_count({ count: 1 }, { locale: 'es' })).toBe('1 seleccionado');
	});
});
