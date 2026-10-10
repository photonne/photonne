import type { PhysicalDuplicateFile, PhysicalDuplicateGroup } from '#lib/api/index.js';

/** A group of identical files on disk and the ones the admin keeps. */
export interface DuplicateReview {
	hash: string;
	files: PhysicalDuplicateFile[];
	/** Physical paths kept; never empty. */
	keep: string[];
}

/** The copy worth keeping: indexed first, then the newest, then the largest. */
export function bestCopy(files: readonly PhysicalDuplicateFile[]) {
	return [...files].sort(
		(a, b) =>
			Number(b.isIndexed) - Number(a.isIndexed) ||
			Date.parse(b.fileModifiedAt) - Date.parse(a.fileModifiedAt) ||
			b.fileSize - a.fileSize
	)[0];
}

export function toReview(group: PhysicalDuplicateGroup): DuplicateReview | null {
	const files = group.files ?? [];
	if (files.length < 2) return null;
	return { hash: group.hash ?? '', files, keep: [bestCopy(files).physicalPath] };
}

export function autoSelect(review: DuplicateReview): DuplicateReview {
	return { ...review, keep: [bestCopy(review.files).physicalPath] };
}

/** Keep ↔ delete for one file; the last kept copy can't be marked for deletion. */
export function toggle(review: DuplicateReview, path: string): DuplicateReview {
	if (review.keep.includes(path)) {
		if (review.keep.length === 1) return review;
		return { ...review, keep: review.keep.filter((kept) => kept !== path) };
	}
	return { ...review, keep: [...review.keep, path] };
}

export function toDelete(reviews: readonly DuplicateReview[]) {
	return reviews.flatMap((review) =>
		review.files.filter((file) => !review.keep.includes(file.physicalPath))
	);
}

/** Drops the files that were deleted, and the groups no longer duplicated. */
export function withoutDeleted(reviews: readonly DuplicateReview[], deleted: ReadonlySet<string>) {
	return reviews
		.map((review) => ({
			...review,
			files: review.files.filter((file) => !deleted.has(file.physicalPath)),
			keep: review.keep.filter((path) => !deleted.has(path))
		}))
		.filter((review) => review.files.length > 1)
		.map((review) => (review.keep.length ? review : autoSelect(review)));
}
