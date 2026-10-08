import { describe, expect, it } from 'vitest';
import type { EnrichmentTaskDto, FaceDto } from '#lib/api/index.js';
import { aiAnalysisRows, busyTypes, isBusy, relativeTime, someFinished } from './ai-analysis';
import { asUtc, canWriteToFile, dateCandidates } from './capture-date';
import { objectLabels, recognizedText, relatedItems, sceneLabels } from './extras';
import { faceBox, samePeoplePersonId, visibleFaces } from './faces';
import { clampFrame, nearestStripFrame, startFrame, stripFrames } from './frames';
import { mapHref, osmHref, photoPosition } from './location';
import { autoAdvanceDelay, parseInterval, slideshowCommand } from './slideshow';

const task = (overrides: Partial<EnrichmentTaskDto>): EnrichmentTaskDto => ({
	taskType: 'FaceRecognition',
	status: 'Completed',
	errorMessage: null,
	attemptCount: 1,
	createdAt: '2026-10-01T10:00:00Z',
	startedAt: null,
	completedAt: '2026-10-01T10:00:05Z',
	nextRetryAt: null,
	...overrides
});

describe('aiAnalysisRows', () => {
	it('gives the five analyses, reading each off its latest task', () => {
		const rows = aiAnalysisRows([
			task({ status: 'Failed', createdAt: '2026-10-01T09:00:00Z', errorMessage: 'boom' }),
			task({ status: 'Completed', createdAt: '2026-10-01T10:00:00Z' }),
			task({ taskType: 'ObjectDetection', status: 'Processing' }),
			task({ taskType: 'textrecognition', status: 'Pending' }),
			task({ taskType: 'Thumbnails', status: 'Failed' })
		]);
		expect(rows.map((row) => [row.analysis.key, row.status.kind])).toEqual([
			['faces', 'done'],
			['objects', 'running'],
			['scenes', 'never'],
			['text', 'queued'],
			['embeddings', 'never']
		]);
		expect(rows[0].status).toEqual({ kind: 'done', at: Date.parse('2026-10-01T10:00:05Z') });
	});

	it('keeps the failure message and when the worker retries', () => {
		const [row] = aiAnalysisRows([
			task({ status: 'Failed', errorMessage: '  ', nextRetryAt: '2026-10-01T11:00:00' })
		]);
		expect(row.status).toEqual({
			kind: 'failed',
			message: null,
			retryAt: Date.parse('2026-10-01T11:00:00Z')
		});
	});

	it('tells busy rows and when one finishes', () => {
		const rows = aiAnalysisRows([
			task({ status: 'Pending' }),
			task({ taskType: 'ObjectDetection', status: 'Suppressed' })
		]);
		expect(isBusy(rows[0].status)).toBe(true);
		expect(isBusy(rows[1].status)).toBe(false);
		expect(busyTypes(rows)).toEqual(['FaceRecognition']);
		expect(someFinished(['FaceRecognition'], [])).toBe(true);
		expect(someFinished(['FaceRecognition'], ['FaceRecognition', 'ImageEmbedding'])).toBe(false);
		expect(someFinished([], [])).toBe(false);
	});
});

describe('relativeTime', () => {
	it('uses the largest unit that fits', () => {
		const now = Date.parse('2026-10-08T12:00:00Z');
		expect(relativeTime(now - 5 * 60_000, now, 'en')).toBe('5 min. ago');
		expect(relativeTime(now + 2 * 3600_000, now, 'en')).toBe('in 2 hr.');
		expect(relativeTime(now - 3 * 86400_000, now, 'en')).toBe('3 days ago');
	});
});

const face = (overrides: Partial<FaceDto>): FaceDto => ({
	id: 'f',
	assetId: 'a',
	personId: null,
	boundingBoxX: 0.1,
	boundingBoxY: 0.2,
	boundingBoxW: 0.1,
	boundingBoxH: 0.15,
	confidence: 0.9,
	isManuallyAssigned: false,
	isRejected: false,
	suggestedPersonId: null,
	suggestedDistance: null,
	...overrides
});

describe('faces', () => {
	it('drops rejected faces and orders the rest left to right', () => {
		const faces = [
			face({ id: 'right', boundingBoxX: 0.7 }),
			face({ id: 'gone', isRejected: true, boundingBoxX: 0 }),
			face({ id: 'left', boundingBoxX: 0.05 })
		];
		expect(visibleFaces(faces).map((f) => f.id)).toEqual(['left', 'right']);
	});

	it('places a box in percentages, kept inside the image', () => {
		expect(faceBox(face({}))).toEqual({ left: '10%', top: '20%', width: '10%', height: '15%' });
		expect(
			faceBox(face({ boundingBoxX: 0.9, boundingBoxY: -0.1, boundingBoxW: 0.3, boundingBoxH: 0.2 }))
		).toEqual({ left: '90%', top: '0%', width: '10%', height: '20%' });
	});

	it('takes the first assigned face for "same people"', () => {
		expect(
			samePeoplePersonId([
				face({ boundingBoxX: 0.8, personId: 'luis' }),
				face({ boundingBoxX: 0.1 }),
				face({ boundingBoxX: 0.4, personId: 'ana' }),
				face({ boundingBoxX: 0, personId: 'nobody', isRejected: true })
			])
		).toBe('ana');
		expect(samePeoplePersonId([face({})])).toBeNull();
	});
});

describe('extras', () => {
	it('joins recognised lines in reading order, or null without text', () => {
		const line = (text: string, lineIndex: number) => ({
			id: String(lineIndex),
			text,
			confidence: 1,
			bBoxX: 0,
			bBoxY: 0,
			bBoxWidth: 0,
			bBoxHeight: 0,
			lineIndex
		});
		expect(recognizedText([line('world', 1), line(' hello ', 0), line(' ', 2)])).toBe(
			'hello\nworld'
		);
		expect(recognizedText([line('  ', 0)])).toBeNull();
	});

	it('lists each object once, most confident first, and scenes by rank', () => {
		const object = (label: string, confidence: number) => ({
			id: label + confidence,
			label,
			classId: 0,
			confidence,
			boundingBoxX: 0,
			boundingBoxY: 0,
			boundingBoxW: 0,
			boundingBoxH: 0
		});
		expect(objectLabels([object('dog', 0.5), object('cat', 0.9), object('Dog', 0.95)])).toEqual([
			'Dog',
			'cat'
		]);
		expect(objectLabels([object('a', 1), object('b', 0.9), object('c', 0.8)], 2)).toEqual([
			'a',
			'b'
		]);
		const scene = (label: string, rank: number) => ({
			id: label,
			label,
			classId: 0,
			confidence: 1,
			rank
		});
		expect(sceneLabels([scene('beach', 2), scene('sky', 1)])).toEqual(['sky', 'beach']);
	});

	it('leaves the shown photo out of a related strip', () => {
		const items = ['a', 'b', 'c', 'd'].map((id) => ({ id }));
		expect(relatedItems(items, 'b', 2)).toEqual([{ id: 'a' }, { id: 'c' }]);
	});
});

describe('capture date', () => {
	const suggestion = {
		currentDate: '2026-01-01T10:00:00',
		currentSource: 'FileSystem',
		exifDate: '2025-07-09T05:36:00',
		inferredDate: '2025-07-09T05:36:00',
		inferredOrigin: 'FileName',
		fileDate: '2026-01-01T10:00:00Z'
	};

	it('reads zone-less dates as UTC', () => {
		expect(asUtc('2025-07-09T05:36:00')).toBe('2025-07-09T05:36:00Z');
		expect(asUtc('2025-07-09T05:36:00+02:00')).toBe('2025-07-09T05:36:00+02:00');
	});

	it('lists each recoverable date once, under its best source, marking the current one', () => {
		expect(dateCandidates(suggestion)).toEqual([
			{ source: 'exif', date: '2025-07-09T05:36:00.000Z', current: false },
			{ source: 'file', date: '2026-01-01T10:00:00.000Z', current: true }
		]);
		expect(
			dateCandidates({ ...suggestion, exifDate: null, inferredOrigin: 'FolderPath' })[0].source
		).toBe('folder');
		expect(dateCandidates(null)).toEqual([]);
	});

	it('offers writing to the file only on editable, non read-only assets', () => {
		expect(canWriteToFile({ canEdit: true, isReadOnly: false })).toBe(true);
		expect(canWriteToFile({ canEdit: true, isReadOnly: true })).toBe(false);
		expect(canWriteToFile({ canEdit: false, isReadOnly: false })).toBe(false);
	});
});

describe('motion frames', () => {
	it('spreads the strip evenly from the first frame to the last', () => {
		expect(stripFrames(91, 10)).toEqual([0, 10, 20, 30, 40, 50, 60, 70, 80, 90]);
		expect(stripFrames(4, 10)).toEqual([0, 1, 2, 3]);
		expect(stripFrames(0)).toEqual([]);
	});

	it('starts in the middle and stays within the clip', () => {
		expect(startFrame(91)).toBe(45);
		expect(startFrame(0)).toBe(0);
		expect(clampFrame(-3, 10)).toBe(0);
		expect(clampFrame(12, 10)).toBe(9);
		expect(nearestStripFrame([0, 10, 20], 14)).toBe(10);
	});
});

describe('location', () => {
	it('accepts real coordinates only', () => {
		expect(photoPosition({ latitude: 41.4, longitude: 2.17 })).toEqual({ lat: 41.4, lng: 2.17 });
		expect(photoPosition({ latitude: 0, longitude: 0 })).toBeNull();
		expect(photoPosition({ latitude: 95, longitude: 2 })).toBeNull();
		expect(photoPosition({ latitude: null, longitude: 2 })).toBeNull();
		expect(photoPosition(null)).toBeNull();
	});

	it('links to the app map and to OpenStreetMap centred on the place', () => {
		expect(mapHref({ lat: 41.4, lng: 2.17 })).toMatch(/\/map\?lat=41\.400000&lng=2\.170000&z=14$/);
		expect(osmHref({ lat: 41.4, lng: 2.17 })).toBe(
			'https://www.openstreetmap.org/?mlat=41.4&mlon=2.17#map=15/41.4/2.17'
		);
	});
});

describe('slideshow', () => {
	it('remembers only the offered intervals', () => {
		expect(parseInterval('10')).toBe(10);
		expect(parseInterval(3)).toBe(3);
		expect(parseInterval('7')).toBe(5);
		expect(parseInterval(null)).toBe(5);
	});

	it('maps keys to commands', () => {
		expect(slideshowCommand(' ')).toBe('toggle');
		expect(slideshowCommand('ArrowRight')).toBe('next');
		expect(slideshowCommand('ArrowLeft')).toBe('previous');
		expect(slideshowCommand('Escape')).toBe('exit');
		expect(slideshowCommand('x')).toBeNull();
	});

	it('advances photos after the interval, and leaves videos and the end alone', () => {
		const base = { running: true, isVideo: false, hasNext: true, interval: 3 as const };
		expect(autoAdvanceDelay(base)).toBe(3000);
		expect(autoAdvanceDelay({ ...base, isVideo: true })).toBeNull();
		expect(autoAdvanceDelay({ ...base, hasNext: false })).toBeNull();
		expect(autoAdvanceDelay({ ...base, running: false })).toBeNull();
	});
});
