import { describe, expect, it } from 'vitest';
import { cardClick, runBulk } from './bulk.js';

describe('runBulk', () => {
	it('runs one at a time and keeps going after a failure', async () => {
		const seen: string[] = [];
		let running = 0;
		const outcome = await runBulk(['a', 'b', 'c', 'd'], async (id) => {
			running++;
			expect(running).toBe(1);
			seen.push(id);
			await Promise.resolve();
			running--;
			if (id === 'c') throw new Error('network');
			return id !== 'b';
		});

		expect(seen).toEqual(['a', 'b', 'c', 'd']);
		expect(outcome).toEqual({ succeeded: ['a', 'd'], failed: ['b', 'c'] });
	});
});

describe('cardClick', () => {
	const plain = { shiftKey: false, ctrlKey: false, metaKey: false };

	it('opens on a plain click until something is selected', () => {
		expect(cardClick(plain, false)).toBe('open');
		expect(cardClick(plain, true)).toBe('toggle');
	});

	it('toggles with Ctrl or Cmd and extends with Shift', () => {
		expect(cardClick({ ...plain, ctrlKey: true }, false)).toBe('toggle');
		expect(cardClick({ ...plain, metaKey: true }, false)).toBe('toggle');
		expect(cardClick({ ...plain, shiftKey: true }, false)).toBe('range');
	});
});
