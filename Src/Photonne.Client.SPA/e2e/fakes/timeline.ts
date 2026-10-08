import type { FakeHandler } from '../fake-api';
import { library } from '../fake-api';

/*
 * The timeline's year view: per-year counts and an evenly spread sample of
 * the fake library, newest first, like GET /api/assets/timeline/years.
 * The asked sample size is recorded in `state.yearSamples`.
 */
const handle: FakeHandler = async ({ method, path, request, authorized, json, state }) => {
	if (method !== 'GET' || path !== '/api/assets/timeline/years') return false;
	if (!authorized) return json(401).then(() => true);
	const sample = Number(new URL(request.url()).searchParams.get('sample') ?? 24);
	((state.yearSamples ??= []) as number[]).push(sample);

	const years = new Map<number, ReturnType<typeof library.items>>();
	for (const bucket of library.buckets) {
		const year = Number(bucket.key.slice(0, 4));
		years.set(year, [...(years.get(year) ?? []), ...library.items(bucket.key)]);
	}
	const body = [...years.entries()]
		.sort(([a], [b]) => b - a)
		.map(([year, items]) => {
			const step = Math.max(1, items.length / sample);
			const picked = Array.from(
				{ length: Math.min(sample, items.length) },
				(_, i) => items[Math.floor(i * step)]
			);
			return { year, count: items.length, items: picked };
		});
	await json(200, body);
	return true;
};

export default handle;
