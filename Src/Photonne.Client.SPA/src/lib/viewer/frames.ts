/** Thumbnails in the frame picker's strip, spread evenly over the clip. */
export const STRIP_FRAMES = 10;

/** `count` frames, `n` of them evenly spaced from the first to the last. */
export function stripFrames(count: number, n = STRIP_FRAMES) {
	if (count <= 0) return [];
	if (count <= n) return Array.from({ length: count }, (_, i) => i);
	return Array.from({ length: n }, (_, i) => Math.round((i * (count - 1)) / (n - 1)));
}

/**
 * Where the picker starts: the still sits around the middle of the clip on
 * both iPhone and Samsung.
 */
export function startFrame(count: number) {
	return count > 0 ? Math.floor(count / 2) : 0;
}

export function clampFrame(index: number, count: number) {
	return Math.min(Math.max(0, Math.round(index)), Math.max(0, count - 1));
}

/** The strip thumbnail nearest to `index`, to mark it as the current one. */
export function nearestStripFrame(strip: readonly number[], index: number) {
	let best = strip[0] ?? 0;
	for (const frame of strip) if (Math.abs(frame - index) < Math.abs(best - index)) best = frame;
	return best;
}

export function frameUrl(assetId: string, index: number) {
	return `/api/assets/${assetId}/motion/frames/${index}`;
}
