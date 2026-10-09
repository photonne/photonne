import type { IndexingCoverageResponse, MonthlyGrowthPoint } from '#lib/api/index.js';

export interface GrowthMonth {
	/** `yyyy-MM`. */
	key: string;
	photos: number;
	videos: number;
}

/**
 * The last `months` months up to `now` (oldest first), with the months that
 * had no new items filled in as zero so the bars keep a steady rhythm.
 */
export function growthSeries(
	points: readonly MonthlyGrowthPoint[],
	months: number,
	now = new Date()
): GrowthMonth[] {
	const byKey = new Map(points.map((p) => [monthKey(p.year, p.month), p]));
	const series: GrowthMonth[] = [];
	for (let back = months - 1; back >= 0; back--) {
		const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1));
		const key = monthKey(date.getUTCFullYear(), date.getUTCMonth() + 1);
		const point = byKey.get(key);
		series.push({ key, photos: point?.photos ?? 0, videos: point?.videos ?? 0 });
	}
	return series;
}

function monthKey(year: number, month: number) {
	return `${year}-${String(month).padStart(2, '0')}`;
}

/**
 * A round axis maximum for the tallest bar: 1, 2 or 5 times a power of ten,
 * so gridlines land on readable numbers.
 */
export function niceMax(value: number) {
	if (value <= 0) return 1;
	const power = 10 ** Math.floor(Math.log10(value));
	for (const step of [1, 2, 5, 10]) {
		if (value <= step * power) return step * power;
	}
	return 10 * power;
}

/**
 * Indexed share of the indexable files (unsupported ones can never be
 * indexed, so they stay out of the ratio), in tenths of a percent to avoid
 * claiming 100 % while a file is still missing.
 */
export function coverageShare(coverage: Pick<IndexingCoverageResponse, 'indexed' | 'unindexed'>) {
	const indexable = coverage.indexed + coverage.unindexed;
	if (indexable === 0 || coverage.unindexed === 0) return 1;
	return Math.floor((coverage.indexed * 1000) / indexable) / 1000;
}

export type Tone = 'success' | 'warning' | 'danger';

/**
 * How worrying a coverage is: a few files out of thousands (99 % or more) is
 * fine, down to 90 % deserves a look, below that something is wrong.
 */
export function coverageTone(share: number): Tone {
	if (share >= 0.99) return 'success';
	if (share >= 0.9) return 'warning';
	return 'danger';
}
