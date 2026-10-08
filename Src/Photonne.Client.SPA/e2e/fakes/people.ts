import type {
	FaceAssignmentResponse,
	MapClusterResponse,
	MapPointResponse,
	PeoplePageResponse,
	PendingCountResponse,
	PersonDto,
	PersonFacesPageResponse,
	PersonSuggestionsPageResponse
} from '../../src/lib/api/generated/types.gen';
import type { FakeHandler } from '../fake-api';
import { library } from '../fake-api';

/** A detected face as the fake server keeps it. */
interface Face {
	id: string;
	assetId: string;
	personId: string | null;
	suggestedPersonId: string | null;
	rejected: boolean;
}

interface Person {
	id: string;
	name: string | null;
	coverFaceId: string | null;
	isHidden: boolean;
	createdAt: string;
	updatedAt: string;
}

/** What the people fake holds for one test; tests read it to assert. */
export interface PeopleState {
	people: Person[];
	faces: Face[];
	unlinked: string[];
	backfills: number;
	reclusters: number;
}

// Photos of the fake library (e2e/fake-api.ts), so the viewer can open them.
const photos = library.buckets.flatMap((bucket) =>
	library.items(bucket.key).filter((item) => item.type === 'Image')
);

const seed: {
	id: string;
	name: string | null;
	faces: number;
	suggestions: number;
	hidden?: boolean;
}[] = [
	{ id: 'person-ana', name: 'Ana', faces: 14, suggestions: 3 },
	{ id: 'person-luis', name: 'Luis', faces: 9, suggestions: 0 },
	{ id: 'person-x1', name: null, faces: 6, suggestions: 0 },
	{ id: 'person-marta', name: 'Marta', faces: 4, suggestions: 1, hidden: true },
	{ id: 'person-x2', name: null, faces: 3, suggestions: 0 },
	{ id: 'person-jose', name: 'José', faces: 2, suggestions: 0 }
];

function initial(): PeopleState {
	const people: Person[] = [];
	const faces: Face[] = [];
	let photo = 0;
	for (const entry of seed) {
		for (let i = 0; i < entry.faces + entry.suggestions; i++) {
			faces.push({
				id: `face-${entry.id.slice(7)}-${i}`,
				assetId: photos[photo++ % photos.length].id,
				personId: i < entry.faces ? entry.id : null,
				suggestedPersonId: i < entry.faces ? null : entry.id,
				rejected: false
			});
		}
		people.push({
			id: entry.id,
			name: entry.name,
			coverFaceId: `face-${entry.id.slice(7)}-0`,
			isHidden: entry.hidden ?? false,
			createdAt: '2026-01-01T00:00:00Z',
			updatedAt: '2026-01-01T00:00:00Z'
		});
	}
	return { people, faces, unlinked: [], backfills: 0, reclusters: 0 };
}

export function peopleState(state: Record<string, unknown>): PeopleState {
	state.people ??= initial();
	return state.people as PeopleState;
}

function dto(s: PeopleState, person: Person): PersonDto {
	const faceCount = s.faces.filter((f) => f.personId === person.id && !f.rejected).length;
	const pendingSuggestionsCount = s.faces.filter(
		(f) => f.suggestedPersonId === person.id && f.personId === null && !f.rejected
	).length;
	return { ...person, faceCount, pendingSuggestionsCount };
}

function fold(text: string) {
	return text
		.normalize('NFD')
		.replace(/\p{Diacritic}/gu, '')
		.toLowerCase();
}

function page<T>(items: T[], url: URL) {
	const offset = Number(url.searchParams.get('offset') ?? 0);
	const limit = Number(url.searchParams.get('limit') ?? 50);
	return { total: items.length, items: items.slice(offset, offset + limit) };
}

const faceSvg = (id: string) => {
	let hash = 0;
	for (const char of id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
	const hue = hash % 360;
	return `<svg xmlns="http://www.w3.org/2000/svg" width="220" height="220"><rect width="220" height="220" fill="hsl(${hue} 40% 70%)"/><circle cx="110" cy="95" r="52" fill="hsl(${(hue + 20) % 360} 45% 82%)"/><circle cx="90" cy="88" r="6" fill="#333"/><circle cx="130" cy="88" r="6" fill="#333"/><path d="M88 118 Q110 136 132 118" stroke="#333" stroke-width="5" fill="none"/><rect x="40" y="170" width="140" height="60" rx="30" fill="hsl(${(hue + 180) % 360} 35% 45%)"/></svg>`;
};

// Map: three places with photos from the library.
const places = [
	{ lat: 41.3874, lng: 2.1686, count: 24 },
	{ lat: 40.4168, lng: -3.7038, count: 9 },
	{ lat: 43.263, lng: -2.935, count: 1 }
];

export const mapPoints: MapPointResponse[] = (() => {
	const points: MapPointResponse[] = [];
	let photo = 0;
	for (const place of places) {
		for (let i = 0; i < place.count; i++) {
			const item = photos[photo++];
			points.push({
				id: item.id,
				latitude: place.lat + (i % 5) * 0.001,
				longitude: place.lng + Math.floor(i / 5) * 0.001,
				hasThumbnail: true,
				date: item.fileCreatedAt
			});
		}
	}
	return points;
})();

function clusters(): MapClusterResponse[] {
	let start = 0;
	return places.map((place) => {
		const members = mapPoints.slice(start, (start += place.count));
		const dates = members.map((p) => p.date).sort();
		return {
			id: `${place.lat}_${place.lng}_${place.count}`,
			latitude: place.lat,
			longitude: place.lng,
			count: place.count,
			assetIds: members.map((p) => p.id),
			earliestDate: dates[0],
			latestDate: dates.at(-1)!,
			firstAssetId: members[0].id,
			hasThumbnail: true
		};
	});
}

const handle: FakeHandler = async ({ method, path, request, authorized, json, route, state }) => {
	const isPeople = path.startsWith('/api/people') || path.startsWith('/api/faces/');
	const url = new URL(request.url());
	const isPersonSearch = path === '/api/assets/search' && url.searchParams.has('personId');
	const isMap = path.startsWith('/api/assets/map');
	const isTileKey =
		path === '/api/settings' && url.searchParams.get('key') === 'ServerSettings.MapTileApiKey';
	if (!isPeople && !isPersonSearch && !isMap && !isTileKey) return false;
	if (!authorized && !path.endsWith('/thumbnail')) return json(401).then(() => true);

	const s = peopleState(state);
	const find = (id: string) => s.people.find((p) => p.id === id);
	const parts = path.split('/').slice(2); // ['people', id, ...] or ['faces', id, ...]
	const ok = () => route.fulfill({ status: 204 }).then(() => true);
	const notFound = () => json(404).then(() => true);
	const done = (body: unknown) => json(200, body).then(() => true);

	if (isTileKey) return done({ key: 'ServerSettings.MapTileApiKey', value: '' });
	// A test sets state.mapEmpty for a library without locations.
	if (method === 'GET' && path === '/api/assets/map/points') {
		return done(state.mapEmpty ? [] : mapPoints);
	}
	if (method === 'GET' && path === '/api/assets/map') return done(state.mapEmpty ? [] : clusters());

	if (isPersonSearch) {
		const personId = url.searchParams.get('personId')!;
		const assetIds = new Set(
			s.faces.filter((f) => f.personId === personId && !f.rejected).map((f) => f.assetId)
		);
		const items = photos.filter((item) => assetIds.has(item.id));
		const offset = Number(url.searchParams.get('offset') ?? 0);
		const size = Number(url.searchParams.get('pageSize') ?? 100);
		return done({
			items: items.slice(offset, offset + size),
			hasMore: offset + size < items.length
		});
	}

	if (parts[0] === 'faces') {
		const face = s.faces.find((f) => f.id === parts[1]);
		if (parts[2] === 'thumbnail') {
			return route
				.fulfill({ status: 200, contentType: 'image/svg+xml', body: faceSvg(parts[1]) })
				.then(() => true);
		}
		if (!face) return notFound();
		if (method === 'DELETE' && parts.length === 2) {
			Object.assign(face, { rejected: true, personId: null, suggestedPersonId: null });
			return ok();
		}
		switch (parts[2]) {
			case 'assign': {
				const body = request.postDataJSON() as {
					personId: string | null;
					newPersonName: string | null;
				};
				let personId = body.personId;
				if (!personId && body.newPersonName) {
					personId = `person-new-${s.people.length}`;
					s.people.push({
						id: personId,
						name: body.newPersonName,
						coverFaceId: face.id,
						isHidden: false,
						createdAt: '2026-10-01T00:00:00Z',
						updatedAt: '2026-10-01T00:00:00Z'
					});
				}
				if (!personId || !find(personId)) return notFound();
				Object.assign(face, { personId, rejected: false, suggestedPersonId: null });
				return done({ id: face.id, personId } satisfies FaceAssignmentResponse);
			}
			case 'unassign':
				Object.assign(face, { personId: null, suggestedPersonId: null });
				return ok();
			case 'accept-suggestion': {
				const personId = face.suggestedPersonId;
				if (!personId) return notFound();
				Object.assign(face, { personId, suggestedPersonId: null });
				return done({ id: face.id, personId } satisfies FaceAssignmentResponse);
			}
			case 'dismiss-suggestion':
				face.suggestedPersonId = null;
				return ok();
		}
		return false;
	}

	// /api/people…
	if (method === 'GET' && path === '/api/people/face-recognition/pending-count') {
		return done({
			unprocessed: 12,
			inQueue: s.backfills ? 12 : 0,
			completed: 840,
			retrying: 0,
			failed: 1,
			processing: 0,
			lastCompletedAt: '2026-10-07T18:20:00Z',
			completedLastMinute: 0
		} satisfies PendingCountResponse);
	}
	if (method === 'POST' && path === '/api/people/face-recognition/backfill') {
		s.backfills++;
		return done({ enqueued: 12, total: 12, elapsedMs: 40 });
	}
	if (method === 'POST' && path === '/api/people/recluster') {
		s.reclusters++;
		return done({ personsCreated: 2 });
	}

	if (method === 'GET' && path === '/api/people') {
		const q = url.searchParams;
		let list = s.people.filter((p) => q.get('includeHidden') === 'true' || !p.isHidden);
		const search = q.get('search');
		if (search) list = list.filter((p) => p.name && fold(p.name).includes(fold(search)));
		const rows = list.map((p) => dto(s, p));
		if (q.get('sort') === 'name') {
			rows.sort((a, b) =>
				a.name && b.name ? a.name.localeCompare(b.name) : a.name ? -1 : b.name ? 1 : 0
			);
		} else {
			rows.sort((a, b) => b.faceCount - a.faceCount);
			if (q.get('unnamedFirst') === 'true') {
				rows.sort((a, b) => Number(a.name !== null) - Number(b.name !== null));
			}
		}
		return done(page(rows, url) satisfies PeoplePageResponse);
	}

	const person = find(parts[1]);
	if (!person) return notFound();

	if (parts.length === 2) {
		if (method === 'GET') return done(dto(s, person));
		if (method === 'PATCH') {
			const name = (request.postDataJSON() as { name: string | null }).name;
			person.name = name?.trim() || null;
			return ok();
		}
	}
	if (method === 'POST' && parts[2] === 'hide') {
		person.isHidden = true;
		return ok();
	}
	if (method === 'POST' && parts[2] === 'unhide') {
		person.isHidden = false;
		return ok();
	}
	if (method === 'POST' && parts[2] === 'merge') {
		const other = find(parts[3]);
		if (!other) return notFound();
		for (const face of s.faces) {
			if (face.personId === other.id) face.personId = person.id;
			if (face.suggestedPersonId === other.id) face.suggestedPersonId = person.id;
		}
		s.people = s.people.filter((p) => p.id !== other.id);
		return ok();
	}
	if (method === 'POST' && parts[2] === 'cover') {
		person.coverFaceId = parts[3];
		return ok();
	}
	if (method === 'GET' && parts[2] === 'faces') {
		const faces = s.faces
			.filter((f) => f.personId === person.id && !f.rejected)
			.map((f) => ({ id: f.id, assetId: f.assetId, confidence: 0.98, isManuallyAssigned: false }));
		return done(page(faces, url) satisfies PersonFacesPageResponse);
	}
	if (method === 'GET' && parts[2] === 'suggestions') {
		const faces = s.faces
			.filter((f) => f.suggestedPersonId === person.id && f.personId === null)
			.map((f) => ({ id: f.id, assetId: f.assetId, confidence: 0.95, suggestedDistance: 0.42 }));
		return done(page(faces, url) satisfies PersonSuggestionsPageResponse);
	}
	if (method === 'POST' && parts[2] === 'suggestions') {
		const pending = s.faces.filter((f) => f.suggestedPersonId === person.id && f.personId === null);
		for (const face of pending) {
			if (parts[3] === 'accept-all') face.personId = person.id;
			face.suggestedPersonId = null;
		}
		return done({ affected: pending.length });
	}
	if (method === 'POST' && parts[2] === 'assets' && parts[4] === 'unlink') {
		const faces = s.faces.filter((f) => f.personId === person.id && f.assetId === parts[3]);
		for (const face of faces) face.personId = null;
		s.unlinked.push(parts[3]);
		return done({ facesDetached: faces.length });
	}
	return false;
};

export default handle;
