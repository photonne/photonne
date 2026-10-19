import type {
	RequestVideoSummaryRequest,
	UpdateVideoSummaryPlanRequest,
	VideoSummaryPlanCandidateResponse,
	VideoSummaryPlanResponse,
	VideoSummaryResponse,
	VideoSummaryYearResponse
} from '../../src/lib/api/generated/types.gen';
import type { FakeHandler } from '../fake-api';

/**
 * Summary videos. A requested summary moves one step each time the list is
 * read — queued, then rendering at 40 %, then done — so a test sees the
 * dialog follow it the way it polls a real render. One asked for with review
 * stops at "planned" with three clips until the page approves them.
 * `context.state.summaries` holds the data and every request body the page sent.
 */
export interface SummariesState {
	list: VideoSummaryResponse[];
	years: VideoSummaryYearResponse[];
	requests: RequestVideoSummaryRequest[];
	/** Answer the next request with not_enough_media. */
	notEnough: boolean;
	plans: Record<string, VideoSummaryPlanResponse>;
	/** Every edit the review page saved, and the summaries it sent to render. */
	edits: UpdateVideoSummaryPlanRequest[];
	renders: string[];
	alternatives: VideoSummaryPlanCandidateResponse[];
}

export function summariesState(state: Record<string, unknown>) {
	return (state.summaries ??= {
		list: [],
		years: [
			{ year: 2026, videos: 42, photos: 900 },
			{ year: 2025, videos: 1, photos: 300 }
		],
		requests: [],
		notEnough: false,
		plans: {},
		edits: [],
		renders: [],
		alternatives: [
			{
				assetId: '2026-03-5',
				isVideo: true,
				capturedAt: '2026-03-02T10:00:00',
				durationSeconds: 30
			},
			{
				assetId: '2026-03-6',
				isVideo: false,
				capturedAt: '2026-03-03T10:00:00',
				durationSeconds: 0
			}
		]
	} satisfies SummariesState) as SummariesState;
}

/** The cut a reviewed summary stops at: two moments of one video and a photo. */
function planOf(summary: VideoSummaryResponse): VideoSummaryPlanResponse {
	const clip = (assetId: string, isVideo: boolean, day: number, start: number, length: number) => ({
		assetId,
		isVideo,
		capturedAt: `2026-03-0${day}T10:00:00`,
		start,
		length,
		sourceSeconds: isVideo ? 20 : 0,
		pinned: false
	});
	return {
		summaryId: summary.id,
		title: summary.title,
		status: 'planned',
		targetSeconds: summary.durationMinutes * 60,
		totalSeconds: 13,
		alternatives: 2,
		clips: [
			clip('2026-03-0', true, 1, 2, 6),
			clip('2026-03-1', true, 1, 12, 4),
			clip('2026-03-2', false, 4, 0, 3)
		]
	};
}

function advance(data: SummariesState, summary: VideoSummaryResponse): VideoSummaryResponse {
	if (summary.status === 'pending' && summary.review && !data.renders.includes(summary.id)) {
		data.plans[summary.id] = planOf(summary);
		return { ...summary, status: 'planned', progress: 1, hasPlan: true, clipCount: 2 };
	}
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

	if (method === 'POST' && path === '/api/video-summaries/music') {
		await json(200, { id: 'music-1', name: 'Verano', durationSeconds: 184 });
		return true;
	}
	if (method === 'GET' && path === '/api/video-summaries/years') {
		await json(200, data.years);
		return true;
	}
	if (method === 'GET' && path === '/api/video-summaries') {
		const answer = data.list;
		data.list = data.list.map((s) => advance(data, s));
		await json(200, answer);
		return true;
	}
	const one = /^\/api\/video-summaries\/([^/]+)(\/.*)?$/.exec(path);
	const summary = one ? data.list.find((s) => s.id === one[1]) : undefined;
	if (one && one[1] !== 'years' && one[1] !== 'music') {
		const rest = one[2] ?? '';
		const plan = summary ? data.plans[summary.id] : undefined;
		if (method === 'GET' && rest === '') {
			if (!summary) return json(404).then(() => true);
			const answer = summary;
			data.list = data.list.map((s) => (s.id === summary.id ? advance(data, s) : s));
			await json(200, answer);
			return true;
		}
		if (!summary || !plan) return json(404).then(() => true);
		if (method === 'GET' && rest === '/plan') {
			await json(200, plan);
			return true;
		}
		if (method === 'GET' && rest === '/plan/alternatives') {
			await json(200, data.alternatives);
			return true;
		}
		if (method === 'PUT' && rest === '/plan') {
			const body = request.postDataJSON() as UpdateVideoSummaryPlanRequest;
			data.edits.push(body);
			const all = [
				...plan.clips,
				...data.alternatives.map((a) => ({ ...a, sourceSeconds: a.durationSeconds }))
			];
			const clips = (body.clips ?? []).map((c) => {
				const known = all.find((k) => k.assetId === c.assetId)!;
				return {
					assetId: c.assetId,
					isVideo: known.isVideo,
					capturedAt: known.capturedAt,
					start: c.start,
					length: c.length,
					sourceSeconds: known.sourceSeconds,
					pinned: c.pinned ?? false
				};
			});
			data.plans[summary.id] = {
				...plan,
				clips,
				totalSeconds: clips.reduce((sum, c) => sum + c.length, 0)
			};
			await json(200, data.plans[summary.id]);
			return true;
		}
		if (method === 'POST' && rest === '/render') {
			data.renders.push(summary.id);
			const pending = { ...summary, status: 'pending', progress: 0, completedAt: null };
			data.list = data.list.map((s) => (s.id === summary.id ? pending : s));
			await json(202, pending);
			return true;
		}
		return false;
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
			orientation: body.orientation ?? 'all',
			durationMinutes: body.durationMinutes ?? 5,
			showTitles: body.showTitles ?? true,
			musicId: body.musicId ?? null,
			musicName: body.musicName ?? null,
			review: body.review ?? false,
			hasPlan: false,
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
