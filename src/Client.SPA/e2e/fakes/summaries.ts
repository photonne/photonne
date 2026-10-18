import type {
	RequestVideoSummaryRequest,
	VideoSummaryResponse,
	VideoSummaryYearResponse
} from '../../src/lib/api/generated/types.gen';
import type { FakeHandler } from '../fake-api';

/**
 * Summary videos. A requested summary moves one step each time the list is
 * read — queued, then rendering at 40 %, then done — so a test sees the
 * dialog follow it the way it polls a real render. `context.state.summaries`
 * holds the data and every request body the page sent.
 */
export interface SummariesState {
	list: VideoSummaryResponse[];
	years: VideoSummaryYearResponse[];
	requests: RequestVideoSummaryRequest[];
	/** Answer the next request with not_enough_media. */
	notEnough: boolean;
}

export function summariesState(state: Record<string, unknown>) {
	return (state.summaries ??= {
		list: [],
		years: [
			{ year: 2026, videos: 42, photos: 900 },
			{ year: 2025, videos: 1, photos: 300 }
		],
		requests: [],
		notEnough: false
	} satisfies SummariesState) as SummariesState;
}

function advance(summary: VideoSummaryResponse): VideoSummaryResponse {
	if (summary.status === 'pending') return { ...summary, status: 'running', progress: 0.4 };
	if (summary.status === 'running')
		return {
			...summary,
			status: 'completed',
			progress: 1,
			assetId: '2026-09-0',
			clipCount: 12,
			completedAt: '2026-10-18T10:05:00Z'
		};
	return summary;
}

const handle: FakeHandler = async ({ method, path, request, authorized, json, state }) => {
	if (!path.startsWith('/api/video-summaries')) return false;
	if (!authorized) return json(401).then(() => true);
	const data = summariesState(state);

	if (method === 'GET' && path === '/api/video-summaries/years') {
		await json(200, data.years);
		return true;
	}
	if (method === 'GET' && path === '/api/video-summaries') {
		const answer = data.list;
		data.list = data.list.map(advance);
		await json(200, answer);
		return true;
	}
	if (method === 'POST' && path === '/api/video-summaries') {
		const body = request.postDataJSON() as RequestVideoSummaryRequest;
		data.requests.push(body);
		if (data.notEnough) {
			await json(400, {
				error: 'No hay suficientes vídeos para montar un resumen.',
				code: 'not_enough_media'
			});
			return true;
		}
		const summary: VideoSummaryResponse = {
			id: `summary-${data.requests.length}`,
			source: body.source ?? 'year',
			year: body.year,
			albumId: body.albumId,
			title: body.source === 'album' ? 'Resumen · Vacaciones' : `Resumen ${body.year}`,
			includePhotos: body.includePhotos,
			status: 'pending',
			progress: 0,
			error: null,
			assetId: null,
			clipCount: 0,
			requestedAt: '2026-10-18T10:00:00Z',
			completedAt: null
		};
		data.list = [summary, ...data.list.filter((s) => s.title !== summary.title)];
		await json(202, summary);
		return true;
	}
	return false;
};

export default handle;
