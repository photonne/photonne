import { sha256Hex } from './checksum.js';
import { isMediaFile, MAX_FILE_BYTES } from './files.js';
import { UploadError, type UploadFailure, type UploadTransport } from './transport.js';

/**
 * - `checking`: being hashed and looked up on the server (skips duplicates
 *   without sending them).
 * - `skipped`: never sent (too large).
 */
export type UploadStatus =
	'checking' | 'queued' | 'uploading' | 'done' | 'duplicate' | 'failed' | 'cancelled' | 'skipped';

export interface UploadEntry {
	id: number;
	file: File;
	status: UploadStatus;
	/** Bytes sent so far, while uploading. */
	loaded: number;
	assetId: string | null;
	failure: UploadFailure | null;
}

export interface UploadCounts {
	total: number;
	pending: number;
	done: number;
	duplicate: number;
	failed: number;
	cancelled: number;
	skipped: number;
}

export interface QueueOptions {
	/** Files sent at once. */
	concurrency?: number;
	/** Files hashed before each server lookup. */
	precheckBatch?: number;
	maxFileBytes?: number;
	hash?: (file: File) => Promise<string | null>;
}

const ACTIVE: readonly UploadStatus[] = ['checking', 'queued', 'uploading'];

/**
 * A queue of files going to the server: duplicates are spotted by checksum
 * before sending (when the transport can look them up), a few files go at a
 * time with byte progress, and each can be cancelled or retried. When the
 * queue runs dry, `ondrained` hears which assets are new.
 */
export class UploadQueue {
	entries = $state<UploadEntry[]>([]);
	/** New assets of the last batch that finished (for "add to album", "view"). */
	lastBatch = $state<string[]>([]);

	counts: UploadCounts = $derived.by(() => {
		const counts = {
			total: this.entries.length,
			pending: 0,
			done: 0,
			duplicate: 0,
			failed: 0,
			cancelled: 0,
			skipped: 0
		};
		for (const entry of this.entries) {
			if (ACTIVE.includes(entry.status)) counts.pending++;
			else if (entry.status === 'done') counts.done++;
			else if (entry.status === 'duplicate') counts.duplicate++;
			else if (entry.status === 'failed') counts.failed++;
			else if (entry.status === 'cancelled') counts.cancelled++;
			else counts.skipped++;
		}
		return counts;
	});

	active = $derived(this.counts.pending > 0);

	/** Overall progress of the files that are (or were) going up, 0..1. */
	progress = $derived.by(() => {
		let total = 0;
		let sent = 0;
		for (const entry of this.entries) {
			if (entry.status === 'skipped' || entry.status === 'cancelled') continue;
			total += entry.file.size;
			if (entry.status === 'uploading') sent += entry.loaded;
			else if (!ACTIVE.includes(entry.status)) sent += entry.file.size;
		}
		return total === 0 ? 0 : sent / total;
	});

	#transport: UploadTransport;
	#options: Required<QueueOptions>;
	#nextId = 1;
	#running = 0;
	#prechecking = false;
	// Plain bookkeeping, never rendered: no need for reactive collections.
	#controllers: Record<number, AbortController> = {};
	#batch: string[] = [];
	#listeners: ((assetIds: string[]) => void)[] = [];

	constructor(transport: UploadTransport, options: QueueOptions = {}) {
		this.#transport = transport;
		this.#options = {
			concurrency: options.concurrency ?? 2,
			precheckBatch: options.precheckBatch ?? 20,
			maxFileBytes: options.maxFileBytes ?? MAX_FILE_BYTES,
			hash: options.hash ?? sha256Hex
		};
	}

	/**
	 * Queues the photos and videos among `files`; anything else is left out
	 * and counted in `ignored`.
	 */
	add(files: readonly File[]): { added: number; ignored: number } {
		const media = files.filter((file) => isMediaFile(file));
		if (media.length === 0) return { added: 0, ignored: files.length };
		if (!this.active) this.lastBatch = [];

		const initial: UploadStatus = this.#transport.precheck ? 'checking' : 'queued';
		this.entries.push(
			...media.map((file) => ({
				id: this.#nextId++,
				file,
				status: file.size > this.#options.maxFileBytes ? ('skipped' as const) : initial,
				loaded: 0,
				assetId: null,
				failure: null
			}))
		);
		this.#precheck();
		this.#pump();
		return { added: media.length, ignored: files.length - media.length };
	}

	cancel(id: number) {
		const entry = this.#find(id);
		if (!entry || !ACTIVE.includes(entry.status)) return;
		entry.status = 'cancelled';
		this.#controllers[id]?.abort();
		this.#settle();
	}

	cancelAll() {
		for (const entry of this.entries) {
			if (ACTIVE.includes(entry.status)) this.cancel(entry.id);
		}
	}

	retry(id: number) {
		const entry = this.#find(id);
		if (!entry || (entry.status !== 'failed' && entry.status !== 'cancelled')) return;
		if (!this.active) this.lastBatch = [];
		entry.status = 'queued';
		entry.failure = null;
		entry.loaded = 0;
		this.#pump();
	}

	retryFailed() {
		for (const entry of this.entries) {
			if (entry.status === 'failed') this.retry(entry.id);
		}
	}

	remove(id: number) {
		this.cancel(id);
		this.entries = this.entries.filter((entry) => entry.id !== id);
	}

	/** Drops everything that is no longer going anywhere. */
	clearFinished() {
		this.entries = this.entries.filter((entry) => ACTIVE.includes(entry.status));
		if (!this.active) this.lastBatch = [];
	}

	/** Called with the new asset ids each time the queue runs dry. */
	ondrained(listener: (assetIds: string[]) => void) {
		this.#listeners = [...this.#listeners, listener];
		return () => {
			this.#listeners = this.#listeners.filter((l) => l !== listener);
		};
	}

	#find(id: number) {
		return this.entries.find((entry) => entry.id === id);
	}

	/** Hashes the waiting files in batches and asks the server which it has. */
	async #precheck() {
		const precheck = this.#transport.precheck;
		if (!precheck || this.#prechecking) return;
		this.#prechecking = true;
		try {
			for (;;) {
				const batch = this.entries
					.filter((entry) => entry.status === 'checking')
					.slice(0, this.#options.precheckBatch);
				if (batch.length === 0) break;

				const sums: Record<number, string> = {};
				for (const entry of batch) {
					const sum = await this.#options.hash(entry.file).catch(() => null);
					if (sum) sums[entry.id] = sum;
				}
				let existing: Record<string, string> = {};
				const unique = Object.values(sums).filter((sum, i, all) => all.indexOf(sum) === i);
				if (unique.length > 0) {
					existing = await precheck(unique).catch(() => ({}));
				}
				for (const { id } of batch) {
					const entry = this.#find(id);
					// Cancelled or removed while it was being checked.
					if (entry?.status !== 'checking') continue;
					const assetId = existing[sums[id] ?? ''];
					if (assetId) {
						entry.status = 'duplicate';
						entry.assetId = assetId;
					} else {
						entry.status = 'queued';
					}
				}
				this.#pump();
			}
		} finally {
			this.#prechecking = false;
			this.#settle();
		}
	}

	#pump() {
		while (this.#running < this.#options.concurrency) {
			const next = this.entries.find((entry) => entry.status === 'queued');
			if (!next) break;
			this.#send(next.id);
		}
		this.#settle();
	}

	async #send(id: number) {
		const entry = this.#find(id)!;
		const controller = new AbortController();
		this.#controllers[id] = controller;
		this.#running++;
		entry.status = 'uploading';
		entry.loaded = 0;
		try {
			const result = await this.#transport.upload(entry.file, {
				signal: controller.signal,
				onprogress: (loaded) => {
					const current = this.#find(id);
					if (current?.status === 'uploading') current.loaded = loaded;
				}
			});
			const current = this.#find(id);
			if (current?.status === 'uploading') {
				current.status = result.duplicate ? 'duplicate' : 'done';
				current.assetId = result.assetId;
				if (!result.duplicate && result.assetId) this.#batch.push(result.assetId);
			}
		} catch (error) {
			const current = this.#find(id);
			if (current?.status === 'uploading') {
				current.status = 'failed';
				current.failure = error instanceof UploadError ? error.failure : 'network';
			}
		} finally {
			delete this.#controllers[id];
			this.#running--;
			this.#pump();
		}
	}

	#settle() {
		if (this.active || this.#running > 0 || this.#prechecking) return;
		const assetIds = this.#batch;
		this.#batch = [];
		if (assetIds.length === 0) return;
		this.lastBatch = assetIds;
		for (const listener of this.#listeners) listener(assetIds);
	}
}
