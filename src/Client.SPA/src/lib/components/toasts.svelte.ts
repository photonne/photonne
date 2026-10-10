export interface Toast {
	id: number;
	message: string;
	tone: 'info' | 'error';
	/** e.g. Undo; the toast closes when it runs. */
	action?: { label: string; run: () => void | Promise<void> };
}

const DURATION_MS = 6000;

/**
 * Short-lived messages at the bottom of the screen, announced politely to
 * screen readers (see Toaster.svelte). An action (Undo) keeps the toast up
 * a little longer than plain information would need.
 */
class Toasts {
	items = $state<Toast[]>([]);
	#next = 1;

	show(message: string, options: { tone?: Toast['tone']; action?: Toast['action'] } = {}) {
		const toast: Toast = {
			id: this.#next++,
			message,
			tone: options.tone ?? 'info',
			action: options.action
		};
		this.items = [...this.items, toast];
		setTimeout(() => this.dismiss(toast.id), options.action ? DURATION_MS * 1.5 : DURATION_MS);
		return toast.id;
	}

	error(message: string) {
		return this.show(message, { tone: 'error' });
	}

	dismiss(id: number) {
		this.items = this.items.filter((toast) => toast.id !== id);
	}
}

export const toasts = new Toasts();
