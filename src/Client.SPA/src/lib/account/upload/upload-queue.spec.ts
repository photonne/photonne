import { describe, expect, it, vi } from 'vitest';
import { UploadError, type UploadOptions, type UploadTransport } from './transport.js';
import { UploadQueue } from './upload-queue.svelte.js';

function file(name: string, size = 10) {
	return new File([new Uint8Array(size)], name, { lastModified: 1_700_000_000_000 });
}

/** A transport whose uploads finish only when the test says so. */
function manualTransport(existing: Record<string, string> = {}) {
	const pending = new Map<
		string,
		{ resolve: (id: string) => void; reject: (e: unknown) => void; options: UploadOptions }
	>();
	const transport: UploadTransport = {
		precheck: vi.fn(async (sums: string[]) =>
			Object.fromEntries(sums.filter((s) => existing[s]).map((s) => [s, existing[s]]))
		),
		upload: (f, options) =>
			new Promise((resolve, reject) => {
				pending.set(f.name, {
					resolve: (id) => resolve({ assetId: id, duplicate: false }),
					reject,
					options
				});
				options.signal.addEventListener('abort', () =>
					reject(new DOMException('Aborted', 'AbortError'))
				);
			})
	};
	return { transport, pending };
}

const hash = async (f: File) => `sum-${f.name}`;
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('UploadQueue', () => {
	it('keeps only photos and videos', () => {
		const queue = new UploadQueue({ upload: vi.fn(() => new Promise<never>(() => {})) });
		const result = queue.add([file('a.jpg'), file('notes.txt'), file('.DS_Store'), file('b.MOV')]);
		expect(result).toEqual({ added: 2, ignored: 2 });
		expect(queue.entries.map((e) => e.file.name)).toEqual(['a.jpg', 'b.MOV']);
	});

	it('skips files over the size limit', () => {
		const queue = new UploadQueue(
			{ upload: vi.fn(() => new Promise<never>(() => {})) },
			{ maxFileBytes: 5 }
		);
		queue.add([file('big.jpg', 6)]);
		expect(queue.entries[0].status).toBe('skipped');
		expect(queue.active).toBe(false);
	});

	it('marks files the server already has as duplicates without sending them', async () => {
		const { transport, pending } = manualTransport({ 'sum-old.jpg': 'asset-old' });
		const queue = new UploadQueue(transport, { hash });
		queue.add([file('old.jpg'), file('new.jpg')]);
		await flush();

		expect(queue.entries[0]).toMatchObject({ status: 'duplicate', assetId: 'asset-old' });
		expect(queue.entries[1].status).toBe('uploading');
		expect([...pending.keys()]).toEqual(['new.jpg']);
	});

	it('sends a limited number at a time and reports the new assets once drained', async () => {
		const { transport, pending } = manualTransport();
		const queue = new UploadQueue(transport, { hash, concurrency: 2 });
		const drained = vi.fn();
		queue.ondrained(drained);
		queue.add([file('1.jpg'), file('2.jpg'), file('3.jpg')]);
		await flush();

		expect(queue.entries.map((e) => e.status)).toEqual(['uploading', 'uploading', 'queued']);
		pending.get('1.jpg')!.options.onprogress(5);
		expect(queue.entries[0].loaded).toBe(5);
		expect(queue.progress).toBeCloseTo(5 / 30);

		pending.get('1.jpg')!.resolve('a1');
		await flush();
		expect(queue.entries[2].status).toBe('uploading');
		pending.get('2.jpg')!.resolve('a2');
		pending.get('3.jpg')!.resolve('a3');
		await flush();

		expect(queue.counts).toMatchObject({ done: 3, pending: 0 });
		expect(queue.progress).toBe(1);
		expect(drained).toHaveBeenCalledExactlyOnceWith(['a1', 'a2', 'a3']);
		expect(queue.lastBatch).toEqual(['a1', 'a2', 'a3']);
	});

	it('records why a file failed and retries it', async () => {
		const { transport, pending } = manualTransport();
		const queue = new UploadQueue(transport, { hash });
		queue.add([file('a.jpg')]);
		await flush();
		pending.get('a.jpg')!.reject(new UploadError('quota'));
		await flush();
		expect(queue.entries[0]).toMatchObject({ status: 'failed', failure: 'quota' });

		queue.retryFailed();
		expect(queue.entries[0]).toMatchObject({ status: 'uploading', failure: null });
		pending.get('a.jpg')!.resolve('a');
		await flush();
		expect(queue.entries[0].status).toBe('done');
	});

	it('cancels an upload in flight and the ones waiting', async () => {
		const { transport } = manualTransport();
		const queue = new UploadQueue(transport, { hash, concurrency: 1 });
		queue.add([file('a.jpg'), file('b.jpg')]);
		await flush();
		queue.cancelAll();
		await flush();
		expect(queue.entries.map((e) => e.status)).toEqual(['cancelled', 'cancelled']);
		expect(queue.active).toBe(false);

		queue.retry(queue.entries[1].id);
		expect(queue.entries[1].status).toBe('uploading');
	});

	it('uploads straight away when the transport cannot look files up', async () => {
		const upload = vi.fn(async () => ({ assetId: null, duplicate: true }));
		const queue = new UploadQueue({ upload });
		queue.add([file('a.jpg')]);
		await flush();
		expect(upload).toHaveBeenCalledOnce();
		expect(queue.entries[0].status).toBe('duplicate');
	});

	it('clears what has finished but keeps what is still going', async () => {
		const { transport, pending } = manualTransport();
		const queue = new UploadQueue(transport, { hash, concurrency: 1 });
		queue.add([file('a.jpg'), file('b.jpg')]);
		await flush();
		pending.get('a.jpg')!.resolve('a');
		await flush();
		queue.clearFinished();
		expect(queue.entries.map((e) => e.file.name)).toEqual(['b.jpg']);
		queue.remove(queue.entries[0].id);
		expect(queue.entries).toEqual([]);
	});
});
