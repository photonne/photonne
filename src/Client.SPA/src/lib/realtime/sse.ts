/**
 * Splits a Server-Sent Events stream into the event names it carries. Fed
 * chunk by chunk (an event can straddle two); comments (": ping") and data
 * lines are skipped — our events only say what changed.
 */
export class SseParser {
	#buffer = '';
	#event = '';

	/** The events completed by [chunk], in order. */
	push(chunk: string): string[] {
		this.#buffer += chunk;
		const events: string[] = [];
		let newline: number;
		while ((newline = this.#buffer.indexOf('\n')) >= 0) {
			const line = this.#buffer.slice(0, newline).replace(/\r$/, '');
			this.#buffer = this.#buffer.slice(newline + 1);
			if (line === '') {
				if (this.#event) events.push(this.#event);
				this.#event = '';
			} else if (line.startsWith('event:')) {
				this.#event = line.slice('event:'.length).trim();
			}
		}
		return events;
	}
}

/** Waits before reconnecting: 1 s, 2 s, 4 s… up to 30 s. */
export function backoff(attempt: number): number {
	return Math.min(30_000, 1000 * 2 ** Math.max(0, attempt));
}
