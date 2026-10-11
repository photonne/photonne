import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { m } from '#lib/paraglide/messages.js';
import { toasts } from './toasts.svelte.js';

describe('toasts', () => {
	beforeEach(() => vi.useFakeTimers());
	afterEach(() => {
		for (const toast of toasts.items) toasts.dismiss(toast.id);
		vi.useRealTimers();
	});

	it('closes on its own after a while', () => {
		toasts.show('Guardado');
		vi.advanceTimersByTime(5999);
		expect(toasts.items).toHaveLength(1);
		vi.advanceTimersByTime(1);
		expect(toasts.items).toHaveLength(0);
	});

	it('waits while paused and goes on with the time left', () => {
		const id = toasts.show('Guardado');
		vi.advanceTimersByTime(4000);
		toasts.pause(id);
		vi.advanceTimersByTime(60_000);
		expect(toasts.items).toHaveLength(1);
		toasts.resume(id);
		vi.advanceTimersByTime(1999);
		expect(toasts.items).toHaveLength(1);
		vi.advanceTimersByTime(1);
		expect(toasts.items).toHaveLength(0);
	});

	it('undoes the newest toast that offers Undo', () => {
		const older = vi.fn();
		const newer = vi.fn();
		const open = vi.fn();
		toasts.show('Archivada', { action: { label: m.action_undo(), run: older } });
		toasts.show('Borrada', { action: { label: m.action_undo(), run: newer } });
		toasts.show('Fotograma guardado', { action: { label: 'Abrir', run: open } });

		expect(toasts.undoLatest()).toBe(true);
		expect(newer).toHaveBeenCalledOnce();
		expect(older).not.toHaveBeenCalled();
		expect(open).not.toHaveBeenCalled();
		expect(toasts.items.map((toast) => toast.message)).toEqual(['Archivada', 'Fotograma guardado']);
	});

	it('does nothing without an Undo on screen', () => {
		toasts.show('Guardado');
		expect(toasts.undoLatest()).toBe(false);
		expect(toasts.items).toHaveLength(1);
	});
});
