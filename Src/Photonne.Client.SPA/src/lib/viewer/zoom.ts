/**
 * Pan/zoom maths for the viewer, kept apart from the DOM so it can be tested.
 * The image is fitted ("contain") into the stage at scale 1; zooming scales
 * around a point, and panning is clamped so the image never leaves an empty
 * band where it could cover the stage.
 */
export interface ZoomState {
	scale: number;
	x: number;
	y: number;
}

export const MIN_SCALE = 1;
export const MAX_SCALE = 8;

export const IDENTITY: ZoomState = { scale: 1, x: 0, y: 0 };

/**
 * Size of the image fitted into the stage at scale 1. Small images aren't
 * blown up past their pixels, unless `upscale` (a thumbnail standing in for
 * an original that is larger).
 */
export function fitSize(
	natural: { width: number; height: number },
	stage: { width: number; height: number },
	upscale = false
) {
	if (natural.width <= 0 || natural.height <= 0)
		return { width: stage.width, height: stage.height };
	const ratio = Math.min(
		stage.width / natural.width,
		stage.height / natural.height,
		upscale ? Infinity : 1
	);
	return { width: natural.width * ratio, height: natural.height * ratio };
}

/**
 * Zooms to `scale` keeping the stage point `focus` (relative to the stage
 * centre) under the pointer.
 */
export function zoomAt(
	state: ZoomState,
	scale: number,
	focus: { x: number; y: number },
	fitted: { width: number; height: number },
	stage: { width: number; height: number }
): ZoomState {
	const next = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
	const factor = next / state.scale;
	return clampPan(
		{
			scale: next,
			x: focus.x - (focus.x - state.x) * factor,
			y: focus.y - (focus.y - state.y) * factor
		},
		fitted,
		stage
	);
}

/** Keeps the image covering the stage along every axis where it is larger. */
export function clampPan(
	state: ZoomState,
	fitted: { width: number; height: number },
	stage: { width: number; height: number }
): ZoomState {
	const maxX = Math.max(0, (fitted.width * state.scale - stage.width) / 2);
	const maxY = Math.max(0, (fitted.height * state.scale - stage.height) / 2);
	return {
		scale: state.scale,
		x: Math.min(maxX, Math.max(-maxX, state.x)),
		y: Math.min(maxY, Math.max(-maxY, state.y))
	};
}
