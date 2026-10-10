import { describe, expect, it, vi } from 'vitest';
import { getDeviceId } from './device-id.js';

function memoryStorage() {
	const values = new Map<string, string>();
	return {
		getItem: (key: string) => values.get(key) ?? null,
		setItem: (key: string, value: string) => void values.set(key, value)
	};
}

describe('getDeviceId', () => {
	it('creates an id once and keeps it', () => {
		const storage = memoryStorage();

		const first = getDeviceId(storage);

		expect(first).toMatch(/^web-[0-9a-f-]{36}$/);
		expect(getDeviceId(storage)).toBe(first);
	});

	it('still works when storage throws', () => {
		const broken = {
			getItem: () => {
				throw new Error('blocked');
			},
			setItem: () => {
				throw new Error('blocked');
			}
		};

		const id = getDeviceId(broken);

		expect(id).toMatch(/^web-/);
		expect(getDeviceId(broken)).toBe(id);
	});

	it('works without crypto.randomUUID (plain http on the local network)', async () => {
		vi.stubGlobal('crypto', {
			getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto)
		});
		vi.resetModules();
		try {
			// A fresh module: the tests above leave an id in its memory.
			const { getDeviceId: fresh } = await import('./device-id.js');
			expect(fresh(memoryStorage())).toMatch(/^web-[0-9a-f]{32}$/);
		} finally {
			vi.unstubAllGlobals();
		}
	});
});
