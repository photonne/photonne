import { describe, expect, it } from 'vitest';
import { directoryOf, fold, labelText, lastSegment, matches } from './text.js';

describe('text helpers', () => {
	it('matches ignoring case and accents', () => {
		expect(fold('José Ñandú')).toBe('jose nandu');
		expect(matches('José', 'jose')).toBe(true);
		expect(matches('Perro', ' RR ')).toBe(true);
		expect(matches('Gato', 'perro')).toBe(false);
	});

	it('makes model labels readable', () => {
		expect(labelText('beach_house')).toBe('Beach house');
		expect(labelText('dog')).toBe('Dog');
	});

	it('splits paths', () => {
		expect(lastSegment('/assets/users/ana/Viajes/')).toBe('Viajes');
		expect(directoryOf('/assets/users/ana/IMG_1.jpg')).toBe('/assets/users/ana');
		expect(directoryOf('IMG_1.jpg')).toBe('IMG_1.jpg');
	});
});
