/**
 * Splits a streamed response body into the JSON objects it carries, as they
 * arrive.
 *
 * The server streams progress in two shapes: NDJSON, one object per line
 * (maintenance kinds, face clustering), and a JSON array written element by
 * element (minimal-API `IAsyncEnumerable`: index, thumbnails, metadata, dates,
 * duplicates and `/api/tasks/{id}/stream`). Both are a sequence of top-level
 * objects, so this looks for balanced braces outside strings and ignores the
 * brackets, commas and newlines in between.
 */
export class JsonObjectSplitter {
	#buffer = '';
	#depth = 0;
	#inString = false;
	#escaped = false;
	#start = -1;
	#scanned = 0;

	/** Feeds a chunk of text; returns every object it completed. */
	push(chunk: string): unknown[] {
		this.#buffer += chunk;
		const objects: unknown[] = [];
		let i = this.#scanned;
		while (i < this.#buffer.length) {
			const char = this.#buffer[i];
			if (this.#depth === 0) {
				if (char === '{') {
					this.#start = i;
					this.#depth = 1;
				}
			} else if (this.#inString) {
				if (this.#escaped) this.#escaped = false;
				else if (char === '\\') this.#escaped = true;
				else if (char === '"') this.#inString = false;
			} else if (char === '"') {
				this.#inString = true;
			} else if (char === '{' || char === '[') {
				this.#depth++;
			} else if (char === '}' || char === ']') {
				this.#depth--;
				if (this.#depth === 0) {
					try {
						objects.push(JSON.parse(this.#buffer.slice(this.#start, i + 1)));
					} catch {
						// A malformed object is skipped; the next one still parses.
					}
					this.#buffer = this.#buffer.slice(i + 1);
					this.#start = -1;
					i = 0;
					continue;
				}
			}
			i++;
		}
		if (this.#depth === 0) {
			// Nothing open: what is left is separators.
			this.#buffer = '';
			this.#scanned = 0;
		} else {
			this.#scanned = this.#buffer.length;
		}
		return objects;
	}
}

/**
 * Reads `body` to the end (or until `onObject` returns false), handing over
 * each object as soon as it is complete.
 */
export async function readJsonObjects(
	body: ReadableStream<Uint8Array>,
	onObject: (value: unknown) => boolean | void
): Promise<void> {
	const reader = body.getReader();
	const decoder = new TextDecoder();
	const splitter = new JsonObjectSplitter();
	try {
		for (;;) {
			const { done, value } = await reader.read();
			if (done) break;
			for (const object of splitter.push(decoder.decode(value, { stream: true }))) {
				if (onObject(object) === false) return;
			}
		}
	} finally {
		reader.cancel().catch(() => {});
	}
}
