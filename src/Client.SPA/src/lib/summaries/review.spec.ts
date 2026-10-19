import { describe, expect, it } from 'vitest';
import type { VideoSummaryPlanResponse } from '#lib/api/index.js';
import {
	captureDay,
	clock,
	problem,
	setEnd,
	setPhotoSeconds,
	setStart,
	swapIn,
	toEditable,
	togglePinned,
	toRequest,
	totalSeconds,
	type EditableClip
} from './review.js';

function plan(): VideoSummaryPlanResponse {
	return {
		summaryId: 's1',
		title: 'Resumen 2024',
		status: 'planned',
		targetSeconds: 60,
		totalSeconds: 13,
		alternatives: 4,
		clips: [
			{
				assetId: 'v1',
				isVideo: true,
				capturedAt: '2024-03-01T10:00:00',
				start: 2,
				length: 6,
				sourceSeconds: 20,
				pinned: false
			},
			{
				assetId: 'v1',
				isVideo: true,
				capturedAt: '2024-03-01T10:00:00',
				start: 12,
				length: 4,
				sourceSeconds: 20,
				pinned: false
			},
			{
				assetId: 'p1',
				isVideo: false,
				capturedAt: '2024-05-01T10:00:00',
				start: 0,
				length: 3,
				sourceSeconds: 0,
				pinned: true
			}
		]
	};
}

describe('toEditable', () => {
	it('tells apart two moments of the same video', () => {
		const clips = toEditable(plan());
		expect(clips).toHaveLength(3);
		expect(new Set(clips.map((c) => c.key)).size).toBe(3);
	});
});

describe('trimming', () => {
	const video: EditableClip = toEditable(plan())[0];

	it('moves the start keeping the end, never past it', () => {
		expect(setStart(video, 4)).toMatchObject({ start: 4, length: 4 });
		expect(setStart(video, 30)).toMatchObject({ start: 7, length: 1 });
		expect(setStart(video, -3)).toMatchObject({ start: 0, length: 8 });
	});

	it('moves the end within the video', () => {
		expect(setEnd(video, 10)).toMatchObject({ start: 2, length: 8 });
		expect(setEnd(video, 99)).toMatchObject({ start: 2, length: 18 });
		expect(setEnd(video, 0)).toMatchObject({ start: 2, length: 1 });
	});

	it('keeps a photo between one and ten seconds', () => {
		const photo = toEditable(plan())[2];
		expect(setPhotoSeconds(photo, 5).length).toBe(5);
		expect(setPhotoSeconds(photo, 0).length).toBe(1);
		expect(setPhotoSeconds(photo, 30).length).toBe(10);
	});
});

describe('swapIn', () => {
	const replaced = toEditable(plan())[0];

	it('gives a video the same length, a little before its middle', () => {
		expect(
			swapIn(replaced, {
				assetId: 'v9',
				isVideo: true,
				capturedAt: '2024-03-02T10:00:00',
				durationSeconds: 26
			})
		).toMatchObject({ assetId: 'v9', start: 8, length: 6, sourceSeconds: 26, pinned: false });
	});

	it('takes all of a video shorter than the clip, and the usual time for a photo', () => {
		const short = swapIn(replaced, {
			assetId: 'v8',
			isVideo: true,
			capturedAt: '2024-03-02T10:00:00',
			durationSeconds: 4
		});
		expect(short).toMatchObject({ start: 0, length: 4 });
		const photo = swapIn(replaced, {
			assetId: 'p8',
			isVideo: false,
			capturedAt: '2024-03-02T10:00:00',
			durationSeconds: 0
		});
		expect(photo).toMatchObject({ isVideo: false, start: 0, length: 3, sourceSeconds: 0 });
	});
});

describe('pinning', () => {
	it('pins and unpins every clip of the asset', () => {
		const pinned = togglePinned(toEditable(plan()), 'v1');
		expect(pinned.map((c) => c.pinned)).toEqual([true, true, true]);
		expect(togglePinned(pinned, 'p1').map((c) => c.pinned)).toEqual([true, true, false]);
	});
});

describe('checks', () => {
	it('adds up the clips and says what is wrong', () => {
		const clips = toEditable(plan());
		expect(totalSeconds(clips)).toBe(13);
		expect(problem(clips, 60)).toBeNull();
		expect(problem(clips, 12)).toBe('too_long');
		expect(problem(clips.slice(0, 1), 60)).toBe('too_few');
	});

	it('sends what the server needs', () => {
		expect(toRequest(toEditable(plan()))[2]).toEqual({
			assetId: 'p1',
			start: 0,
			length: 3,
			pinned: true
		});
	});
});

describe('formatting', () => {
	it('writes times as m:ss', () => {
		expect(clock(0)).toBe('0:00');
		expect(clock(65.4)).toBe('1:05');
		expect(clock(600)).toBe('10:00');
	});

	it('shows the capture day without shifting it', () => {
		expect(captureDay('2024-03-01T23:30:00', 'es-ES')).toBe('1 mar 2024');
		expect(captureDay('2024-03-01T00:30:00Z', 'es-ES')).toBe('1 mar 2024');
	});
});
