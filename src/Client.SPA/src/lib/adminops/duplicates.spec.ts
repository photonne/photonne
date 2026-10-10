import { describe, expect, it } from 'vitest';
import type { PhysicalDuplicateFile } from '#lib/api/index.js';
import { autoSelect, bestCopy, toDelete, toggle, toReview, withoutDeleted } from './duplicates.js';

function file(path: string, over: Partial<PhysicalDuplicateFile> = {}): PhysicalDuplicateFile {
	return {
		physicalPath: path,
		virtualPath: path,
		fileName: path,
		directory: '/',
		fileSize: 100,
		fileModifiedAt: '2026-01-01T00:00:00Z',
		isIndexed: false,
		assetId: null,
		ownerUsername: null,
		...over
	};
}

describe('duplicates', () => {
	it('keeps the indexed copy, then the newest, then the largest', () => {
		const a = file('a', { fileModifiedAt: '2026-05-01T00:00:00Z' });
		const b = file('b', { isIndexed: true });
		expect(bestCopy([a, b]).physicalPath).toBe('b');
		const c = file('c', { fileModifiedAt: '2026-06-01T00:00:00Z' });
		expect(bestCopy([a, c]).physicalPath).toBe('c');
		expect(bestCopy([file('d'), file('e', { fileSize: 200 })]).physicalPath).toBe('e');
	});

	it('reviews groups of two or more, never letting the last copy go', () => {
		expect(toReview({ hash: 'h', files: [file('a')] })).toBeNull();
		const review = toReview({ hash: 'h', files: [file('a', { isIndexed: true }), file('b')] })!;
		expect(review.keep).toEqual(['a']);
		expect(toggle(review, 'a')).toBe(review);
		const both = toggle(review, 'b');
		expect(both.keep).toEqual(['a', 'b']);
		expect(toggle(both, 'a').keep).toEqual(['b']);
		expect(autoSelect(both).keep).toEqual(['a']);
	});

	it('lists what to delete and drops it afterwards', () => {
		const one = toReview({ hash: '1', files: [file('a', { isIndexed: true }), file('b')] })!;
		const two = toReview({
			hash: '2',
			files: [file('c', { isIndexed: true }), file('d'), file('e')]
		})!;
		expect(toDelete([one, two]).map((f) => f.physicalPath)).toEqual(['b', 'd', 'e']);
		const left = withoutDeleted([one, two], new Set(['b', 'd']));
		expect(left.map((r) => r.hash)).toEqual(['2']);
		expect(left[0].files.map((f) => f.physicalPath)).toEqual(['c', 'e']);
	});
});
