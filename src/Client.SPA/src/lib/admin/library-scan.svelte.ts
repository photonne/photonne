import {
	cancelBackgroundTask,
	getBackgroundTasks,
	scanLibraryStream,
	streamBackgroundTask
} from '#lib/api/index.js';
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
		const { data } = await scanLibraryStream({
			path: { id: libraryId },
			parseAs: 'stream',
			signal: abort.signal
		}).catch(() => ({ data: undefined }));
		await this.#follow(data, abort);
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
		const { data } = await streamBackgroundTask({
			path: { id: task.id },
			parseAs: 'stream',
			signal: abort.signal
		}).catch(() => ({ data: undefined }));
		await this.#follow(data, abort);
	}

	/** Asks the server to stop the scan; the stream then reports it cancelled. */
	async cancel() {
		if (!this.scan) return;
		this.scan = { ...this.scan, cancelled: true };
		const taskId = this.scan.taskId;
		if (taskId) await cancelBackgroundTask({ path: { id: taskId } });
		else this.stop();
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

	async #follow(body: unknown, abort: AbortController) {
		if (body instanceof ReadableStream) {
			try {
				for await (const update of readJsonArray<ScanUpdate>(body)) {
					if (!this.scan) break;
					this.scan = applyScanUpdate(this.scan, update);
					if (this.scan.done) break;
				}
			} catch {
				// Aborted or the connection dropped: handled below.
			}
		}
		if (abort.signal.aborted || !this.scan) return;
		// A stream that ends without a final update lost the connection.
		const finished: ScanState = this.scan.done
			? this.scan
			: { ...this.scan, done: true, error: this.scan.error ?? 'connection' };
		this.scan = null;
		this.#abort = null;
		this.#onfinish(finished);
	}
}
