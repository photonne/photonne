import { describe, expect, it, vi } from 'vitest';
import type { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
import { leaveViewerThen, returnToRestored } from './viewer-flow.js';

function fakeViewer(openId: string) {
	const viewer = {
		openId: openId as string | null,
		navigate: vi.fn(async (id: string) => {
			viewer.openId = id;
		}),
		close: vi.fn(async () => {
			viewer.openId = null;
			return null;
		})
	};
	return viewer;
}

describe('viewer flow', () => {
	it('an undo shows again the photo the viewer moved past', async () => {
		const viewer = fakeViewer('b');
		await leaveViewerThen(viewer as unknown as ViewerRoute, ['a', 'b', 'c'], async () => {});
		expect(viewer.openId).toBe('c');

		await returnToRestored(['b']);
		expect(viewer.navigate).toHaveBeenLastCalledWith('b');
		expect(viewer.openId).toBe('b');
	});

	it('does nothing once the viewer has closed', async () => {
		const viewer = fakeViewer('c');
		await leaveViewerThen(viewer as unknown as ViewerRoute, ['c'], async () => {});
		expect(viewer.close).toHaveBeenCalled();

		await returnToRestored(['c']);
		expect(viewer.navigate).not.toHaveBeenCalled();
	});
});
