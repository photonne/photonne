import { describe, expect, it } from 'vitest';
import { clampWidth } from './pane-width.js';

describe('clampWidth', () => {
	it('keeps the width within its bounds', () => {
		expect(clampWidth(100, 200, 600)).toBe(200);
		expect(clampWidth(900, 200, 600)).toBe(600);
		expect(clampWidth(321.6, 200, 600)).toBe(322);
	});
});
