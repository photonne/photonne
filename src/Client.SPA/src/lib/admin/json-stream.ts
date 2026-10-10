/**
 * Incremental reader for a JSON array that the server writes element by
 * element (ASP.NET streams an `IAsyncEnumerable<T>` as `[{…},{…},…]`): each
 * element is handed out as soon as its closing brace arrives, so a long task
 * reports progress while it runs.
 *
 * Elements must be objects or arrays (progress updates are). They are cut
 * out at their closing bracket and parsed with JSON.parse; brackets and
 * escaped quotes inside strings are skipped.
 */
export class JsonArrayParser<T = unknown> {
	#buffer = '';
	#depth = 0;
	#inString = false;
	#escaped = false;
	#started = false;
	#start = -1;
	#scanned = 0;

	/** Feeds a chunk of text; returns the elements it completed. */
	push(chunk: string): T[] {
		const out: T[] = [];
		this.#buffer += chunk;
		const text = this.#buffer;
		let i = this.#scanned;
		for (; i < text.length; i++) {
			const char = text[i];
			if (this.#inString) {
				if (this.#escaped) this.#escaped = false;
				else if (char === '\\') this.#escaped = true;
				else if (char === '"') this.#inString = false;
				continue;
			}
			if (!this.#started) {
				if (char === '[') this.#started = true;
				continue;
			}
			if (char === '"') {
				this.#inString = true;
			} else if (char === '{' || char === '[') {
				if (this.#depth === 0) this.#start = i;
				this.#depth++;
			} else if (char === '}' || char === ']') {
				if (this.#depth === 0) continue; // the array's own closing bracket
				this.#depth--;
				if (this.#depth === 0) {
					out.push(JSON.parse(text.slice(this.#start, i + 1)) as T);
					this.#start = -1;
				}
			}
		}
		// Drop what is fully consumed so the buffer stays small.
		const keep = this.#start >= 0 ? this.#start : i;
		this.#buffer = text.slice(keep);
		if (this.#start >= 0) this.#start = 0;
		this.#scanned = i - keep;
		return out;
	}
}

/** The elements of a streamed JSON array body, as they arrive. */
export async function* readJsonArray<T>(body: ReadableStream<Uint8Array>): AsyncGenerator<T> {
	const parser = new JsonArrayParser<T>();
	const decoder = new TextDecoder();
	const reader = body.getReader();
	try {
		while (true) {
			const { value, done } = await reader.read();
			if (done) return;
			yield* parser.push(decoder.decode(value, { stream: true }));
		}
	} finally {
		// Stopping early (the page closes) also stops the download.
		await reader.cancel().catch(() => {});
	}
}
