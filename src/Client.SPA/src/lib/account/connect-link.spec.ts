import { describe, expect, it } from 'vitest';
import { connectLink } from './connect-link.js';

describe('connectLink', () => {
	it('carries the server address, encoded and without a trailing slash', () => {
		expect(connectLink('https://fotos.example.com:8443/')).toBe(
			'photonne://connect?server=https%3A%2F%2Ffotos.example.com%3A8443'
		);
	});
});
