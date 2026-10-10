import { apiErrorCode, streamBackgroundTask } from '#lib/api/index.js';
import { readJsonObjects } from './json-stream.js';
import { toProgressEvent, type ProgressEvent } from './progress.js';

/** What the generated client returns for a call made with `parseAs: 'stream'`. */
interface StreamResult {
	data?: unknown;
	error?: unknown;
	response?: Response;
}

/** Opens one of the progress streams; the generated operation behind it, curried. */
export type StreamStarter = (signal: AbortSignal) => Promise<StreamResult>;

/** A stream the server refused (4xx/5xx), with its `ApiError` text when it sent one. */
export class StreamError extends Error {
	constructor(
		readonly status: number | null,
		readonly code: string | null,
		message: string
	) {
		super(message);
	}
}

function errorText(error: unknown) {
	if (typeof error === 'object' && error !== null && 'error' in error) {
		const text = (error as { error: unknown }).error;
		if (typeof text === 'string') return text;
	}
	return typeof error === 'string' ? error : '';
}

async function open(start: StreamStarter, signal: AbortSignal) {
	const { data, error, response } = await start(signal);
	// The client reports an aborted fetch as an error result; keep it apart
	// from a refusal.
	if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
	if (error !== undefined || !response?.ok || !(data instanceof ReadableStream)) {
		throw new StreamError(response?.status ?? null, apiErrorCode(error), errorText(error));
	}
	return data as ReadableStream<Uint8Array>;
}

/**
 * Starts a job that the server runs as a background task, and lets go. The
 * request is held only until the first event (which carries the task id) or
 * `timeoutMs`; the server keeps working after the connection drops, and the
 * task shows up in `/api/tasks`, where it is followed from then on.
 *
 * Resolves with the first event, or null when none came in time. Rejects with
 * a `StreamError` when the server refused the request.
 */
export async function trigger(start: StreamStarter, timeoutMs = 3000) {
	const controller = new AbortController();
	const timer = setTimeout(() => controller.abort(), timeoutMs);
	try {
		const body = await open(start, controller.signal);
		let first: ProgressEvent | null = null;
		await readJsonObjects(body, (value) => {
			first = toProgressEvent(value);
			return first === null;
		});
		return first as ProgressEvent | null;
	} catch (error) {
		// Timing out is not a failure: the job is running, just quiet.
		if (controller.signal.aborted && !(error instanceof StreamError)) return null;
		throw error;
	} finally {
		clearTimeout(timer);
		controller.abort();
	}
}

/**
 * Reads a progress stream to its end, handing over every event. Resolves with
 * the last event (null if none). Aborting `signal` ends it quietly.
 */
export async function follow(
	start: StreamStarter,
	onEvent: (event: ProgressEvent) => void,
	signal: AbortSignal
) {
	let last: ProgressEvent | null = null;
	try {
		const body = await open(start, signal);
		await readJsonObjects(body, (value) => {
			const event = toProgressEvent(value);
			if (event) {
				last = event;
				onEvent(event);
			}
		});
	} catch (error) {
		if (!signal.aborted) throw error;
	}
	return last as ProgressEvent | null;
}

/** The resume stream of a task the server is running: replays from the start, then live. */
export function taskStream(id: string): StreamStarter {
	return (signal) => streamBackgroundTask({ path: { id }, parseAs: 'stream', signal });
}
