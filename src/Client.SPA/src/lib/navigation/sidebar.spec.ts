import { afterEach, describe, expect, it, vi } from 'vitest';
import { initialCollapsed, saveCollapsed } from './sidebar.js';

function memoryStorage() {
	const values = new Map<string, string>();
	return {
		getItem: (key: string) => values.get(key) ?? null,
		setItem: (key: string, value: string) => void values.set(key, value)
	};
}

describe('sidebar state', () => {
	afterEach(() => vi.unstubAllGlobals());

	it('collapses on a narrow window until the user chooses', () => {
		vi.stubGlobal('localStorage', memoryStorage());
		expect(initialCollapsed(1024)).toBe(true);
		expect(initialCollapsed(1440)).toBe(false);
	});

	it('keeps the user choice whatever the width', () => {
		vi.stubGlobal('localStorage', memoryStorage());
		saveCollapsed(false);
		expect(initialCollapsed(1024)).toBe(false);
		saveCollapsed(true);
		expect(initialCollapsed(1440)).toBe(true);
	});
});
