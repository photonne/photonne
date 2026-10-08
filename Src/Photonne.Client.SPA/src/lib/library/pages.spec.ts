import { describe, expect, it, vi } from 'vitest';

vi.mock('#lib/paraglide/runtime.js', () => ({ getLocale: () => 'es' }));

const { offsetPage, sharedTrashToTimeline } = await import('./pages.js');
const { dateRange } = await import('./dates.js');

describe('offsetPage', () => {
	it('carries the next offset as the cursor', async () => {
		const fetch = vi.fn(async () => ({ items: new Array(50).fill({}), hasMore: true }));

		const first = await offsetPage(null, 50, fetch);
		expect(fetch).toHaveBeenLastCalledWith(0);
		expect(first).toMatchObject({ hasMore: true, nextCursor: '50' });

		await offsetPage(first!.nextCursor!, 50, fetch);
		expect(fetch).toHaveBeenLastCalledWith(50);
	});

	it('stops on an empty page and passes failures on', async () => {
		expect(await offsetPage('100', 50, async () => ({ items: [], hasMore: true }))).toMatchObject({
			hasMore: false
		});
		expect(await offsetPage(null, 50, async () => undefined)).toBeUndefined();
	});
});

describe('sharedTrashToTimeline', () => {
	it('dates a shared-trash item by its deletion and keeps its shape', () => {
		const item = sharedTrashToTimeline({
			id: 'a',
			fileName: 'IMG_1.jpg',
			fullPath: '/assets/shared/Familia/IMG_1.jpg',
			fileSize: 10,
			type: 'Video',
			extension: '.mp4',
			hasThumbnails: true,
			width: 1920,
			height: 1080,
			deletedAt: '2026-10-05T09:00:00Z',
			deletedByUsername: 'luis',
			deletedFromPath: '/assets/shared/Familia',
			deletedFromFolderName: 'Familia'
		});

		expect(item).toMatchObject({
			id: 'a',
			type: 'Video',
			fileCreatedAt: '2026-10-05T09:00:00Z',
			aspectRatio: 1920 / 1080
		});
	});
});

describe('dates', () => {
	it('shows a window of whole days without its exclusive end', () => {
		expect(dateRange('2024-10-08T00:00:00Z', '2024-10-09T00:00:00Z')).toBe('8 oct 2024');
		expect(dateRange('2024-07-01T00:00:00Z', '2024-08-01T00:00:00Z')).toBe('1–31 jul 2024');
	});
});
