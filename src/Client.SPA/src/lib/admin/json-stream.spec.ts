import { describe, expect, it } from 'vitest';
import { JsonArrayParser, readJsonArray } from './json-stream.js';

describe('JsonArrayParser', () => {
	it('hands out each element as soon as it closes', () => {
		const parser = new JsonArrayParser<{ n: number }>();

		expect(parser.push('[{"n":1},{"n"')).toEqual([{ n: 1 }]);
		expect(parser.push(':2}')).toEqual([{ n: 2 }]);
		expect(parser.push(',{"n":3}]')).toEqual([{ n: 3 }]);
	});

	it('ignores brackets and escaped quotes inside strings', () => {
		const parser = new JsonArrayParser<{ m: string; a?: number[] }>();
		const text = '[{"m":"a } ] \\" {","a":[1,[2]]},{"m":"\\\\"}]';

		const out = [...text].flatMap((char) => parser.push(char));

		expect(out).toEqual([{ m: 'a } ] " {', a: [1, [2]] }, { m: '\\' }]);
	});

	it('handles whitespace, newlines and an empty array', () => {
		expect(new JsonArrayParser().push(' [ ]')).toEqual([]);
		expect(new JsonArrayParser().push('[\n  {"a": 1},\n  {"b": 2}\n]')).toEqual([
			{ a: 1 },
			{ b: 2 }
		]);
	});
});

describe('readJsonArray', () => {
	it('reads a streamed body', async () => {
		const encoder = new TextEncoder();
		const body = new ReadableStream<Uint8Array>({
			start(controller) {
				for (const part of ['[{"p":1', '0},', '{"p":100}]'])
					controller.enqueue(encoder.encode(part));
				controller.close();
			}
		});

		const out: unknown[] = [];
		for await (const item of readJsonArray(body)) out.push(item);

		expect(out).toEqual([{ p: 10 }, { p: 100 }]);
	});
});
