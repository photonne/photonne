/** A timeline month and how many photos it holds (TimelineBucketResponse). */
export interface MonthCount {
	key: string;
	count: number;
}

/** Years that have photos, newest first, from the `yyyy-MM` month keys. */
export function yearsWithPhotos(months: readonly MonthCount[]): number[] {
	const years = new Set<number>();
	for (const month of months) if (month.count > 0) years.add(Number(month.key.slice(0, 4)));
	return [...years].sort((a, b) => b - a);
}

/** January to December of `year`, each with its key and photo count (0 if none). */
export function monthsOf(months: readonly MonthCount[], year: number) {
	const counts = new Map(months.map((month) => [month.key, month.count]));
	return Array.from({ length: 12 }, (_, index) => {
		const key = `${year}-${String(index + 1).padStart(2, '0')}`;
		return { key, month: index + 1, count: counts.get(key) ?? 0 };
	});
}
