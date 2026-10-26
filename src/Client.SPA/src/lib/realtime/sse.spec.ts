import { describe, expect, it } from 'vitest';
import { backoff, SseParser } from './sse.js';

describe('SseParser', () => {
	it('names each event once it ends, across chunks, skipping comments', () => {
		const parser = new SseParser();
		expect(parser.push(': connected\n\nevent: lib')).toEqual([]);
		expect(
			parser.push('rary\ndata: {}\n\n: ping\n\nevent: notifications\r\ndata: {}\r\n\r\n')
		).toEqual(['library', 'notifications']);
	});
});

describe('backoff', () => {
	it('doubles up to half a minute', () => {
		expect([0, 1, 2, 10].map(backoff)).toEqual([1000, 2000, 4000, 30_000]);
	});
});
