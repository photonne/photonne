import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';
import { StreamError } from './streams.js';

/**
 * The server's own words for a refusal (`ApiError.error`, e.g. "Detección de
 * objetos está desactivado en Ajustes"), or `fallback` for anything else.
 * A 404 on an action means this server doesn't know it: an older version.
 */
export function describeError(error: unknown, fallback: string = m.ops_error_generic()) {
	if (error instanceof StreamError) {
		if (error.status === 404 && !error.message) return m.ops_error_unknown_action();
		return error.message || fallback;
	}
	if (typeof error === 'object' && error !== null && 'error' in error) {
		const text = (error as { error: unknown }).error;
		if (typeof text === 'string' && text) return text;
	}
	return fallback;
}

export function toastError(error: unknown, fallback?: string) {
	toasts.error(describeError(error, fallback));
}

/** A ticking "now" for relative times ("hace 3 min") on a page that stays open. */
export class Clock {
	now = $state(Date.now());

	/** Starts ticking; returns the cleanup for `$effect`. */
	start(intervalMs = 15_000) {
		const id = setInterval(() => (this.now = Date.now()), intervalMs);
		return () => clearInterval(id);
	}
}
