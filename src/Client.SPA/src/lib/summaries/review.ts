import type {
	VideoSummaryPlanCandidateResponse,
	VideoSummaryPlanClipRequest,
	VideoSummaryPlanResponse
} from '#lib/api/index.js';

/** One clip of the cut while the user edits it. `key` tells clips of the same
 * video apart (a long video can give two or three moments). */
export interface EditableClip {
	key: string;
	assetId: string;
	isVideo: boolean;
	capturedAt: string;
	start: number;
	length: number;
	/** The whole video's length; 0 for photos. */
	sourceSeconds: number;
	pinned: boolean;
}

/** Shortest a video clip may be, and the bounds of a photo's time on screen
 * (the server checks the same). */
export const MIN_CLIP_SECONDS = 1;
export const MIN_PHOTO_SECONDS = 1;
export const MAX_PHOTO_SECONDS = 10;
export const PHOTO_SECONDS = 3;

let nextKey = 0;
const newKey = () => `clip-${++nextKey}`;

export function toEditable(plan: VideoSummaryPlanResponse): EditableClip[] {
	return plan.clips.map((c) => ({ ...c, key: newKey() }));
}

const round = (n: number) => Math.round(n * 10) / 10;

/** Moves where a video clip starts, keeping where it ends; never closer than
 * the minimum to its end. */
export function setStart(clip: EditableClip, start: number): EditableClip {
	const end = clip.start + clip.length;
	const clamped = Math.min(Math.max(0, start), end - MIN_CLIP_SECONDS);
	return { ...clip, start: round(clamped), length: round(end - round(clamped)) };
}

/** Moves where a video clip ends, within the video. */
export function setEnd(clip: EditableClip, end: number): EditableClip {
	const clamped = Math.min(Math.max(clip.start + MIN_CLIP_SECONDS, end), clip.sourceSeconds);
	return { ...clip, length: round(clamped - clip.start) };
}

/** How long a photo stays on screen. */
export function setPhotoSeconds(clip: EditableClip, seconds: number): EditableClip {
	return {
		...clip,
		length: Math.min(MAX_PHOTO_SECONDS, Math.max(MIN_PHOTO_SECONDS, round(seconds)))
	};
}

/** The clip a swapped-in asset gets: as long as the one it replaces when it
 * can be, from a little before the middle of a video. */
export function swapIn(
	replaced: EditableClip,
	alternative: VideoSummaryPlanCandidateResponse
): EditableClip {
	const base = {
		key: newKey(),
		assetId: alternative.assetId,
		isVideo: alternative.isVideo,
		capturedAt: alternative.capturedAt,
		pinned: false
	};
	if (!alternative.isVideo) return { ...base, start: 0, length: PHOTO_SECONDS, sourceSeconds: 0 };
	const length = round(
		Math.min(Math.max(replaced.length, MIN_CLIP_SECONDS), alternative.durationSeconds)
	);
	const start = round(Math.max(0, (alternative.durationSeconds - length) * 0.4));
	return { ...base, start, length, sourceSeconds: alternative.durationSeconds };
}

/** Pins or unpins an asset: every clip of it follows. */
export function togglePinned(clips: EditableClip[], assetId: string): EditableClip[] {
	const pinned = !clips.find((c) => c.assetId === assetId)?.pinned;
	return clips.map((c) => (c.assetId === assetId ? { ...c, pinned } : c));
}

export function totalSeconds(clips: readonly EditableClip[]): number {
	return clips.reduce((sum, c) => sum + c.length, 0);
}

/** Why the cut can't be saved as it is, if it can't. */
export function problem(
	clips: readonly EditableClip[],
	targetSeconds: number
): 'too_few' | 'too_long' | null {
	if (clips.length < 2) return 'too_few';
	if (totalSeconds(clips) > targetSeconds + 0.5) return 'too_long';
	return null;
}

export function toRequest(clips: readonly EditableClip[]): VideoSummaryPlanClipRequest[] {
	return clips.map(({ assetId, start, length, pinned }) => ({ assetId, start, length, pinned }));
}

/** "m:ss", for lengths and positions within a video. */
export function clock(seconds: number): string {
	const whole = Math.max(0, Math.round(seconds));
	return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}

/** The day a clip was captured. Capture times are wall-clock times without a
 * zone, so they're read and shown as UTC to never shift a day. */
export function captureDay(value: string, locale: string): string {
	const iso = /[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`;
	return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' }).format(
		new Date(iso)
	);
}
