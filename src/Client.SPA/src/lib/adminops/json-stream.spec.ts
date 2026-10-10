import { describe, expect, it } from 'vitest';
import { JsonObjectSplitter, readJsonObjects } from './json-stream.js';

describe('JsonObjectSplitter', () => {
	it('splits NDJSON', () => {
		const splitter = new JsonObjectSplitter();
		expect(splitter.push('{"a":1}\n{"a":2}\n')).toEqual([{ a: 1 }, { a: 2 }]);
	});

	it('splits a JSON array written element by element', () => {
		const splitter = new JsonObjectSplitter();
		expect(splitter.push('[{"a":1},')).toEqual([{ a: 1 }]);
		expect(splitter.push('{"a":2}')).toEqual([{ a: 2 }]);
		expect(splitter.push(']')).toEqual([]);
	});

	it('holds an object cut across chunks until it is complete', () => {
		const splitter = new JsonObjectSplitter();
		expect(splitter.push('{"message":"Proce')).toEqual([]);
		expect(splitter.push('sando","stats":{"n":[1,2]}')).toEqual([]);
		expect(splitter.push('}\n{"x"')).toEqual([{ message: 'Procesando', stats: { n: [1, 2] } }]);
		expect(splitter.push(':true}')).toEqual([{ x: true }]);
	});

	it('ignores braces and escaped quotes inside strings', () => {
		const splitter = new JsonObjectSplitter();
		expect(splitter.push('{"m":"a } \\" { b"}')).toEqual([{ m: 'a } " { b' }]);
	});

	it('skips a malformed object and keeps going', () => {
		const splitter = new JsonObjectSplitter();
		expect(splitter.push('{bad}\n{"ok":1}')).toEqual([{ ok: 1 }]);
	});
});

describe('readJsonObjects', () => {
	function body(...chunks: string[]) {
		const encoder = new TextEncoder();
		return new ReadableStream<Uint8Array>({
			start(controller) {
				for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
				controller.close();
			}
		});
	}

	it('hands over every object of the stream', async () => {
		const seen: unknown[] = [];
		await readJsonObjects(body('[{"p":1', '0},{"p":20}]'), (value) => {
			seen.push(value);
		});
		expect(seen).toEqual([{ p: 10 }, { p: 20 }]);
	});

	it('stops when the callback says so', async () => {
		const seen: unknown[] = [];
		await readJsonObjects(body('{"p":1}\n{"p":2}\n'), (value) => {
			seen.push(value);
			return false;
		});
		expect(seen).toEqual([{ p: 1 }]);
	});
});
