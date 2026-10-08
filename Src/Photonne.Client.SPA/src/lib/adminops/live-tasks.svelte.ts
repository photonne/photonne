import type { BackgroundTaskResponse } from '#lib/api/index.js';
import type { ProgressEvent } from './progress.js';
import { follow, taskStream } from './streams.js';
import { isRunning, withLive } from './tasks.js';

const RETRY_AFTER_MS = 5000;

/**
 * Keeps a live stream open on every running background task, so progress
 * moves per item instead of per poll. Fed with each poll of `/api/tasks`
 * (`sync`); a stream that ends asks for a fresh poll (`onEnded`), which is
 * also how a task started elsewhere, or before the page opened, gets picked
 * up.
 */
export class LiveTasks {
	events = $state<Record<string, ProgressEvent>>({});
	// Bookkeeping only, never rendered: plain collections on purpose.
	// eslint-disable-next-line svelte/prefer-svelte-reactivity
	#streams = new Map<string, AbortController>();
	/** When each stream last ended: a task still "Running" right after its
	 * stream ended waits a little before being followed again, so a broken
	 * connection can't spin follow → poll → follow. */
	// eslint-disable-next-line svelte/prefer-svelte-reactivity
	#ended = new Map<string, number>();
	#onEnded: () => void;

	constructor(onEnded: () => void) {
		this.#onEnded = onEnded;
	}

	sync(tasks: readonly BackgroundTaskResponse[]) {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- a local lookup
		const running = new Set(tasks.filter(isRunning).map((task) => task.id));
		for (const [id, controller] of this.#streams) {
			if (!running.has(id)) {
				controller.abort();
				this.#streams.delete(id);
				delete this.events[id];
			}
		}
		const now = Date.now();
		for (const id of running) {
			if (this.#streams.has(id) || now - (this.#ended.get(id) ?? 0) < RETRY_AFTER_MS) continue;
			this.#follow(id);
		}
	}

	/** The task as polled, with its live progress over it. */
	apply(task: BackgroundTaskResponse) {
		return withLive(task, this.events[task.id]);
	}

	stop() {
		for (const controller of this.#streams.values()) controller.abort();
		this.#streams.clear();
		this.events = {};
	}

	#follow(id: string) {
		const controller = new AbortController();
		this.#streams.set(id, controller);
		follow(taskStream(id), (event) => (this.events[id] = event), controller.signal)
			.catch(() => null)
			.then(() => {
				if (controller.signal.aborted || this.#streams.get(id) !== controller) return;
				this.#streams.delete(id);
				this.#ended.set(id, Date.now());
				delete this.events[id];
				this.#onEnded();
			});
	}
}
