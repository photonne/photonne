import type { FaceDto } from '#lib/api/index.js';

const clamp01 = (value: number) => Math.min(1, Math.max(0, value));

/**
 * The faces worth showing, left to right as they appear in the photo (the
 * order the list and the boxes are read in). Rejected ones ("not a face")
 * are gone for the user.
 */
export function visibleFaces(faces: readonly FaceDto[]) {
	return faces
		.filter((face) => !face.isRejected)
		.sort((a, b) => a.boundingBoxX - b.boundingBoxX || a.boundingBoxY - b.boundingBoxY);
}

/**
 * Where a face box goes over the photo, in percentages of the image: the
 * server stores [x, y, w, h] normalised to [0, 1], so the box follows the
 * image whatever size it is drawn at. Kept inside the image.
 */
export function faceBox(
	face: Pick<FaceDto, 'boundingBoxX' | 'boundingBoxY' | 'boundingBoxW' | 'boundingBoxH'>
) {
	const x = clamp01(face.boundingBoxX);
	const y = clamp01(face.boundingBoxY);
	const pct = (value: number) => `${+(value * 100).toFixed(3)}%`;
	return {
		left: pct(x),
		top: pct(y),
		width: pct(Math.min(clamp01(face.boundingBoxW), 1 - x)),
		height: pct(Math.min(clamp01(face.boundingBoxH), 1 - y))
	};
}

/**
 * The person behind the "same people" strip: the first face (left to right)
 * that someone is assigned to. Same rule as the native app.
 */
export function samePeoplePersonId(faces: readonly FaceDto[]) {
	return visibleFaces(faces).find((face) => face.personId)?.personId ?? null;
}
