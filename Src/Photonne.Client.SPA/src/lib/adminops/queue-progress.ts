/**
 * How far along the run this page queued is: what completed since it was
 * queued, over that plus what is still queued or in flight. The lifetime
 * ratio would start at 90 % on a library that is mostly processed and barely
 * move while the new work happens. Null without a run to measure.
 */
export function queueProgress(
	counts: { completed: number; inQueue: number; processing: number },
	baseline: number | null
) {
	if (baseline === null) return null;
	const done = Math.max(0, counts.completed - baseline);
	const left = counts.inQueue + counts.processing;
	if (done + left === 0) return null;
	return (done / (done + left)) * 100;
}
