import { m } from '#lib/paraglide/messages.js';

export interface Toast {
	id: number;
	message: string;
	tone: 'info' | 'error';
	/** e.g. Undo; the toast closes when it runs. */
	action?: { label: string; run: () => void | Promise<void> };
}

const DURATION_MS = 6000;

/** A toast's countdown; it stops while the pointer or the focus is on it. */
interface Timer {
	handle: ReturnType<typeof setTimeout> | null;
	remaining: number;
	startedAt: number;
}

/**
 * Short-lived messages at the bottom of the screen, announced to screen
 * readers (see Toaster.svelte). An action (Undo) keeps the toast up a little
 * longer than plain information would need.
 */
class Toasts {
	items = $state<Toast[]>([]);
	#next = 1;
	#timers = new Map<number, Timer>();

	show(message: string, options: { tone?: Toast['tone']; action?: Toast['action'] } = {}) {
		const toast: Toast = {
			id: this.#next++,
			message,
			tone: options.tone ?? 'info',
			action: options.action
		};
		this.items = [...this.items, toast];
		const duration = options.action ? DURATION_MS * 1.5 : DURATION_MS;
		this.#timers.set(toast.id, { handle: null, remaining: duration, startedAt: 0 });
		this.resume(toast.id);
		return toast.id;
	}

	error(message: string) {
		return this.show(message, { tone: 'error' });
	}

	dismiss(id: number) {
		const timer = this.#timers.get(id);
		if (timer?.handle) clearTimeout(timer.handle);
		this.#timers.delete(id);
		this.items = this.items.filter((toast) => toast.id !== id);
	}

	/** Stops the countdown while the user reads the toast or reaches for its action. */
	pause(id: number) {
		const timer = this.#timers.get(id);
		if (!timer?.handle) return;
		clearTimeout(timer.handle);
		timer.handle = null;
		timer.remaining -= Date.now() - timer.startedAt;
	}

	/** Picks the countdown up where pause() left it. */
	resume(id: number) {
		const timer = this.#timers.get(id);
		if (!timer || timer.handle) return;
		timer.startedAt = Date.now();
		timer.handle = setTimeout(() => this.dismiss(id), Math.max(timer.remaining, 0));
	}

	/**
	 * Runs the Undo of the newest toast that offers one (Ctrl/Cmd+Z); false
	 * when none does. Every area words it as action_undo ("Deshacer"/"Undo"),
	 * which tells it apart from other actions such as "Open".
	 */
	undoLatest(): boolean {
		const toast = this.items.findLast((item) => item.action?.label === m.action_undo());
		if (!toast?.action) return false;
		const { run } = toast.action;
		this.dismiss(toast.id);
		void run();
		return true;
	}
}

export const toasts = new Toasts();
