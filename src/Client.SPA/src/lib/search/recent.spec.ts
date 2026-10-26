import { describe, expect, it } from 'vitest';
import { MAX_RECENT, parseRecent, remember } from './recent.js';

const entry = (params: string, label = params) => ({ params, label });

describe('remember', () => {
	it('puts the newest first and moves a repeated search to the front', () => {
		const list = remember(remember([entry('q=a')], entry('q=b')), entry('q=a'));
		expect(list.map((item) => item.params)).toEqual(['q=a', 'q=b']);
	});

	it(`keeps at most ${MAX_RECENT}`, () => {
		let list = [entry('q=0')];
		for (let i = 1; i <= 20; i++) list = remember(list, entry(`q=${i}`));
		expect(list).toHaveLength(MAX_RECENT);
		expect(list[0].params).toBe('q=20');
	});

	it('ignores an empty search', () => {
		expect(remember([entry('q=a')], entry('', ''))).toEqual([entry('q=a')]);
	});
});

describe('parseRecent', () => {
	it('reads what was stored and drops what is not a search', () => {
		const raw = JSON.stringify([entry('q=a', 'a'), { params: 3 }, null, entry('', 'x')]);
		expect(parseRecent(raw)).toEqual([entry('q=a', 'a')]);
		expect(parseRecent('not json')).toEqual([]);
		expect(parseRecent(null)).toEqual([]);
	});
});
