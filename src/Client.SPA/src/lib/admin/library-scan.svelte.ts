import {
	cancelBackgroundTask,
	getBackgroundTasks,
	scanLibraryStream,
	streamBackgroundTask
} from '#lib/api/index.js';
import { errorText } from './errors.js';
import { readJsonArray } from './json-stream.js';
import { applyScanUpdate, startScan, type ScanState, type ScanUpdate } from './libraries.js';

/**
 * One external-library scan at a time, followed live from the server's
 * progress stream. The scan itself runs on the server: leaving the page only
 * stops listening, and coming back re-attaches to it (`resume`).
 */
export class LibraryScanner {
	scan = $state<ScanState | null>(null);
	#abort: AbortController | null = null;
	#onfinish: (scan: ScanState) => void;

	constructor(onfinish: (scan: ScanState) => void) {
		this.#onfinish = onfinish;
	}

	get running() {
		return this.scan !== null && !this.scan.done;
	}

	async start(libraryId: string) {
		if (this.running) return;
		this.scan = startScan(libraryId);
		const abort = this.#listen();
		const result = await scanLibraryStream({
			path: { id: libraryId },
			parseAs: 'stream',
			signal: abort.signal
		}).catch(() => undefined);
		await this.#follow(result, abort);
	}

	/** Re-attaches to a library scan that is already running on the server. */
	async resume() {
		if (this.running) return;
		const { data: tasks } = await getBackgroundTasks().catch(() => ({ data: undefined }));
		const task = tasks?.find((t) => t.type === 'LibraryScan' && t.status === 'Running');
		const libraryId = task?.parameters?.libraryId;
		if (!task || !libraryId) return;
		this.scan = {
			...startScan(libraryId, true, Math.round(task.percentage)),
			taskId: task.id,
			message: task.lastMessage
		};
		const abort = this.#listen();
		const result = await streamBackgroundTask({
			path: { id: task.id },
			parseAs: 'stream',
			signal: abort.signal
		}).catch(() => undefined);
		await this.#follow(result, abort);
	}

	/**
	 * Asks the server to stop the scan; the stream then reports it cancelled.
	 * Before the first update there is no task id yet: the request goes out as
	 * soon as the stream names the task. Returns the error to show, if any.
	 */
	async cancel(): Promise<string | null> {
		if (!this.scan || this.scan.cancelled) return null;
		this.scan = { ...this.scan, cancelled: true };
		const taskId = this.scan.taskId;
		if (!taskId) return null;
		const { error } = await cancelBackgroundTask({ path: { id: taskId } }).catch((e: unknown) => ({
			error: e ?? 'network'
		}));
		if (!error) return null;
		if (this.scan?.taskId === taskId) this.scan = { ...this.scan, cancelled: false };
		return errorText(error);
	}

	/** Stops listening (the page is going away); the server carries on. */
	stop() {
		this.#abort?.abort();
		this.#abort = null;
	}

	#listen() {
		this.stop();
		this.#abort = new AbortController();
		return this.#abort;
	}

	async #follow(
		result: { data?: unknown; error?: unknown; response?: Response } | undefined,
		abort: AbortController
	) {
		const body = result?.data;
		let cancelSent = !!this.scan?.cancelled && !!this.scan.taskId;
		if (body instanceof ReadableStream) {
			try {
				for await (const update of readJsonArray<ScanUpdate>(body)) {
					if (!this.scan) break;
					this.scan = applyScanUpdate(this.scan, update);
					if (this.scan.done) break;
					// Stop asked for before the server had named the task.
					if (this.scan.cancelled && !cancelSent && this.scan.taskId) {
						cancelSent = true;
						void this.#cancelLate(this.scan.taskId);
					}
				}
			} catch {
				// Aborted or the connection dropped: handled below.
			}
		}
		if (abort.signal.aborted) {
			// Left the page: forget the scan unless a newer one took over.
			if (this.#abort === null) this.scan = null;
			return;
		}
		if (!this.scan) return;
		let finished: ScanState;
		if (this.scan.done) {
			finished = this.scan;
		} else if (result?.response && !result.response.ok) {
			// The server refused to start (validation, demo mode…): nothing runs there.
			finished = { ...this.scan, done: true, rejected: true, error: errorText(result.error) };
		} else if (this.scan.cancelled && !this.scan.taskId) {
			// A stream that ends without a final update lost the connection, here
			// before the stop request could name the task.
			finished = { ...this.scan, done: true, cancelled: false, error: 'cancel-lost' };
		} else {
			finished = { ...this.scan, done: true, error: this.scan.error ?? 'connection' };
		}
		this.scan = null;
		this.#abort = null;
		this.#onfinish(finished);
	}

	async #cancelLate(taskId: string) {
		const { error } = await cancelBackgroundTask({ path: { id: taskId } }).catch((e: unknown) => ({
			error: e ?? 'network'
		}));
		if (error && this.scan?.taskId === taskId) this.scan = { ...this.scan, cancelled: false };
	}
}
