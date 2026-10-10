import type {
	ClassifiedSceneDto,
	DetectedObjectDto,
	RecognizedTextLineDto
} from '#lib/api/index.js';

/** Chips per kind: past this they stop telling the photo apart. */
export const MAX_LABELS = 8;

/** Photos per related strip (same day, same people). */
export const MAX_RELATED = 8;

/** The recognised lines in reading order, one per line; null when there is no text. */
export function recognizedText(lines: readonly RecognizedTextLineDto[]) {
	const text = [...lines]
		.sort((a, b) => a.lineIndex - b.lineIndex)
		.map((line) => line.text.trim())
		.filter(Boolean)
		.join('\n');
	return text || null;
}

function distinctLabels(labels: string[], max: number) {
	const seen = new Set<string>();
	const result: string[] = [];
	for (const raw of labels) {
		const label = raw.trim();
		const key = label.toLocaleLowerCase();
		if (!label || seen.has(key)) continue;
		seen.add(key);
		result.push(label);
		if (result.length === max) break;
	}
	return result;
}

/** Object labels, the most confident first, each once (a photo can hold three dogs). */
export function objectLabels(objects: readonly DetectedObjectDto[], max = MAX_LABELS) {
	return distinctLabels(
		[...objects].sort((a, b) => b.confidence - a.confidence).map((o) => o.label),
		max
	);
}

/** Scene labels in the classifier's rank order, each once. */
export function sceneLabels(scenes: readonly ClassifiedSceneDto[], max = MAX_LABELS) {
	return distinctLabels(
		[...scenes].sort((a, b) => a.rank - b.rank).map((s) => s.label),
		max
	);
}

/** A related strip: without the photo being shown, at most `max`. */
export function relatedItems<T extends { id: string }>(
	items: readonly T[],
	currentId: string,
	max = MAX_RELATED
) {
	return items.filter((item) => item.id !== currentId).slice(0, max);
}
