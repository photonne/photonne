import { describe, expect, it } from 'vitest';
import type { MemoryResponse } from '#lib/api/index.js';
import { memoryArea, memoryBlocks, themeRows } from './memories.js';

const memory = (
	id: string,
	kind: string,
	windowEnd: string,
	extra: Partial<MemoryResponse> = {}
): MemoryResponse => ({
	id,
	kind,
	title: id,
	subtitle: null,
	themeKey: '',
	groupTitle: '',
	cardLabel: null,
	coverAssetId: null,
	assetCount: 3,
	companionPersonId: null,
	companionName: null,
	windowStart: windowEnd,
	windowEnd,
	...extra
});

describe('memoryArea', () => {
	it('keeps journeys in time in Recuerdos and themes in Explorar', () => {
		expect(memoryArea('OnThisDay')).toBe('memories');
		expect(memoryArea('personThroughYears')).toBe('memories');
		expect(memoryArea('PeopleTogether')).toBe('people');
		expect(memoryArea('Trip')).toBe('explore');
		// A kind this build doesn't know yet is still shown somewhere.
		expect(memoryArea('Seasons')).toBe('explore');
	});
});

describe('memoryBlocks', () => {
	it('orders the blocks and puts the newest year first', () => {
		const blocks = memoryBlocks([
			memory('person', 'PersonThroughYears', '2026-01-01T00:00:00Z'),
			memory('today-2019', 'OnThisDay', '2019-10-09T00:00:00Z'),
			memory('trip', 'Trip', '2025-06-01T00:00:00Z'),
			memory('today-2024', 'OnThisDay', '2024-10-09T00:00:00Z')
		]);

		expect(blocks.map((b) => [b.block, b.memories.map((m) => m.id)])).toEqual([
			['today', ['today-2024', 'today-2019']],
			['years', ['person']]
		]);
	});

	it('keeps the server order for people', () => {
		const blocks = memoryBlocks([
			memory('ana', 'PersonThroughYears', '2020-01-01T00:00:00Z'),
			memory('luis', 'PersonThroughYears', '2026-01-01T00:00:00Z')
		]);

		expect(blocks[0].memories.map((m) => m.id)).toEqual(['ana', 'luis']);
	});
});

describe('themeRows', () => {
	it('folds a theme scattered by score into one row, newest first', () => {
		const beach = { themeKey: 'scene:beach', groupTitle: 'Días de playa' };
		const rows = themeRows([
			memory('beach-2021', 'CuratedScene', '2021-08-01T00:00:00Z', beach),
			memory('lisboa', 'Trip', '2025-06-10T00:00:00Z', {
				themeKey: 'trips',
				groupTitle: 'Viajes'
			}),
			memory('today', 'OnThisDay', '2024-10-09T00:00:00Z'),
			memory('beach-2024', 'CuratedScene', '2024-08-01T00:00:00Z', beach),
			memory('fav-2023', 'FavoritesOfYear', '2024-01-01T00:00:00Z', {
				themeKey: 'favorites',
				groupTitle: 'Favoritas del año'
			})
		]);

		expect(rows.map((row) => [row.title, row.memories.map((m) => m.id)])).toEqual([
			['Viajes', ['lisboa']],
			['Favoritas del año', ['fav-2023']],
			['Días de playa', ['beach-2024', 'beach-2021']]
		]);
	});

	it('falls back to the kind and title when the server sent no theme', () => {
		const rows = themeRows([memory('pets', 'PetsAndFood', '2024-01-01T00:00:00Z')]);

		expect(rows).toEqual([{ key: 'PetsAndFood', title: 'pets', memories: [expect.anything()] }]);
	});
});
