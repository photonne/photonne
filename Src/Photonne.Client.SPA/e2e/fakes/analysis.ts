import type { PendingAssetDto } from '../../src/lib/api/generated/types.gen';
import type { FakeHandler } from '../fake-api';

/*
 * My photos' analysis status (GET /api/assets/enrichment/pending) and its
 * retries, with state: a retry moves failed tasks back to pending, and every
 * retry is recorded in `calls`.
 */

export interface AnalysisState {
	items: PendingAssetDto[];
	calls: string[];
}

function seed(): AnalysisState {
	return {
		items: [
			{
				assetId: '2026-09-0',
				fileName: 'IMG_202609_0.jpg',
				fileCreatedAt: '2026-09-28T10:00:00Z',
				pending: 2,
				processing: 1,
				failed: 0,
				failedTaskTypes: []
			},
			{
				assetId: '2026-09-1',
				fileName: 'IMG_202609_1.jpg',
				fileCreatedAt: '2026-09-27T10:00:00Z',
				pending: 0,
				processing: 0,
				failed: 2,
				failedTaskTypes: ['FaceRecognition', 'ObjectDetection']
			},
			{
				assetId: '2026-08-3',
				fileName: 'IMG_202608_3.jpg',
				fileCreatedAt: '2026-08-25T10:00:00Z',
				pending: 0,
				processing: 0,
				failed: 1,
				failedTaskTypes: ['Thumbnails']
			}
		],
		calls: []
	};
}

export function analysisState(state: Record<string, unknown>): AnalysisState {
	return (state.analysis ??= seed()) as AnalysisState;
}

const handle: FakeHandler = async ({ method, path, request, authorized, json, state }) => {
	const retry = /^\/api\/assets\/([^/]+)\/enrichment\/(retry|retry-all)$/.exec(path);
	if (path !== '/api/assets/enrichment/pending' && !retry) return false;
	if (!authorized) return json(401).then(() => true);
	const s = analysisState(state);

	if (method === 'GET' && !retry) {
		await json(200, {
			items: s.items,
			nextCursor: null,
			totalAssets: s.items.length,
			inFlightAssets: s.items.filter((i) => i.pending + i.processing > 0).length,
			failedAssets: s.items.filter((i) => i.failed > 0).length
		});
		return true;
	}

	if (method !== 'POST' || !retry) return false;
	const [, assetId, kind] = retry;
	const item = s.items.find((candidate) => candidate.assetId === assetId);
	if (!item) return json(404, { error: 'Not found', code: 'asset_not_found' }).then(() => true);
	if (kind === 'retry') {
		const taskType = new URL(request.url()).searchParams.get('taskType')!;
		s.calls.push(`retry ${assetId} ${taskType}`);
		item.failedTaskTypes = item.failedTaskTypes.filter((type) => type !== taskType);
		item.failed--;
		item.pending++;
		await json(200, { assetId, taskType, status: 'Pending' });
		return true;
	}
	s.calls.push(`retry-all ${assetId}`);
	const retried = item.failed;
	item.pending += item.failed;
	item.failed = 0;
	item.failedTaskTypes = [];
	await json(200, { assetId, retried });
	return true;
};

export default handle;
