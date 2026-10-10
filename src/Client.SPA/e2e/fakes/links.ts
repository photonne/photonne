import type { FakeContext, FakeHandler } from '../fake-api';

/** A link as GET /api/share/sent returns it. */
interface SentLink {
	token: string;
	createdAt: string;
	expiresAt: string | null;
	hasPassword: boolean;
	allowDownload: boolean;
	maxViews: number | null;
	viewCount: number;
	allowUpload: boolean;
	uploadCount: number;
	assetId: string | null;
	assetFileName: string | null;
	assetType: string | null;
	assetThumbnailUrl: string | null;
	albumId: string | null;
	albumName: string | null;
	albumCoverUrl: string | null;
	shareUrl: string;
}

export interface LinksState {
	links: SentLink[];
	albums: { id: string; name: string }[];
	/** Every call this fake answered, with its JSON body. */
	log: { call: string; body?: unknown }[];
	/** Makes POST /api/share fail, to test the clean-up of a half-made share. */
	failShare: boolean;
}

/**
 * Timeline assets the fake server treats as RAW or HEIC (see
 * /api/assets/download-options): the second and third photo of 2026-09.
 */
export const convertible: Record<string, string> = { '2026-09-1': 'dng', '2026-09-2': 'heic' };

function sent(overrides: Partial<SentLink>): SentLink {
	return {
		token: 'tok',
		createdAt: '2026-10-01T10:00:00Z',
		expiresAt: null,
		hasPassword: false,
		allowDownload: true,
		maxViews: null,
		viewCount: 0,
		allowUpload: false,
		uploadCount: 0,
		assetId: null,
		assetFileName: null,
		assetType: null,
		assetThumbnailUrl: null,
		albumId: null,
		albumName: null,
		albumCoverUrl: null,
		shareUrl: '/share/tok',
		...overrides
	};
}

function seed(): LinksState {
	return {
		links: [
			sent({
				token: 'boda',
				albumId: 'album-1',
				albumName: 'Vacaciones',
				albumCoverUrl: '/api/assets/2026-09-0/thumbnail?size=Medium',
				viewCount: 4,
				maxViews: 10,
				hasPassword: true,
				shareUrl: 'https://fotos.example/share/boda'
			}),
			sent({
				token: 'perro',
				createdAt: '2026-09-20T10:00:00Z',
				assetId: '2026-09-5',
				assetFileName: 'IMG_202609_5.jpg',
				assetType: 'Image',
				assetThumbnailUrl: '/api/assets/2026-09-5/thumbnail?size=Medium',
				viewCount: 1,
				allowDownload: false,
				shareUrl: '/share/perro'
			}),
			sent({
				token: 'cumple',
				createdAt: '2026-09-01T10:00:00Z',
				albumId: 'album-2',
				albumName: 'Cumpleaños',
				expiresAt: '2026-09-15T21:59:59Z',
				shareUrl: '/share/cumple'
			})
		],
		albums: [],
		log: [],
		failShare: false
	};
}

export function linksState(context: { state: Record<string, unknown> }): LinksState {
	return (context.state.links ??= seed()) as LinksState;
}

const handle: FakeHandler = async (context: FakeContext) => {
	const { method, path, request, json, route } = context;
	const s = linksState(context);
	const body = () => (request.postData() ? request.postDataJSON() : undefined);
	const log = (extra?: unknown) => s.log.push({ call: `${method} ${path}`, body: extra });
	const ok = (value: unknown) => json(200, value).then(() => true);

	if (method === 'POST' && path === '/api/assets/download-options') {
		const { assetIds } = body() as { assetIds: string[] };
		log({ assetIds });
		const extensions = assetIds.map((id) => convertible[id]).filter(Boolean);
		return ok({
			total: assetIds.length,
			convertibleCount: extensions.length,
			extensions: [...new Set(extensions)].sort()
		});
	}

	if (method === 'POST' && path === '/api/assets/download-zip') {
		log(body());
		await route.fulfill({
			status: 200,
			contentType: 'application/zip',
			body: Buffer.from('PK\u0005\u0006' + '\u0000'.repeat(18), 'binary')
		});
		return true;
	}

	// The album that wraps shared photos.
	if (method === 'POST' && path === '/api/albums') {
		const { name } = body();
		log({ name });
		const created = { id: `shared-${s.albums.length + 1}`, name };
		s.albums.push(created);
		return ok({
			...created,
			description: null,
			createdAt: '2026-10-08T10:00:00Z',
			updatedAt: '2026-10-08T10:00:00Z',
			assetCount: 0,
			coverThumbnailUrl: null,
			previewThumbnailUrls: [],
			isOwner: true,
			isShared: false,
			sharedWithCount: 0,
			canRead: true,
			canWrite: true,
			canDelete: true,
			canManagePermissions: true,
			hasActiveShareLink: false,
			isPinned: false,
			pinnedAt: null,
			kind: 'Manual'
		});
	}

	if (method === 'DELETE' && /^\/api\/albums\/shared-\d+$/.test(path)) {
		log();
		s.albums = s.albums.filter((a) => a.id !== path.split('/')[3]);
		await route.fulfill({ status: 204 });
		return true;
	}

	if (method === 'GET' && path === '/api/share/sent') return ok(s.links);

	if (method === 'POST' && path === '/api/share') {
		const create = body();
		log(create);
		if (s.failShare) return json(500, { error: 'boom', code: 'internal' }).then(() => true);
		const album = s.albums.find((a) => a.id === create.albumId);
		const token = `nuevo${s.links.length + 1}`;
		const link = sent({
			token,
			createdAt: '2026-10-08T10:00:00Z',
			expiresAt: create.expiresAt,
			hasPassword: !!create.password,
			allowDownload: create.allowDownload,
			maxViews: create.maxViews,
			allowUpload: create.allowUpload,
			albumId: create.albumId,
			albumName: album?.name ?? null,
			shareUrl: `/share/${token}`
		});
		s.links.unshift(link);
		const { token: t, createdAt, expiresAt, hasPassword, allowDownload, maxViews } = link;
		return ok({
			token: t,
			albumId: link.albumId,
			createdAt,
			expiresAt,
			hasPassword,
			allowDownload,
			maxViews,
			viewCount: 0,
			allowUpload: link.allowUpload,
			uploadCount: 0,
			shareUrl: link.shareUrl
		});
	}

	const match = /^\/api\/share\/([^/]+)$/.exec(path);
	if (match && (method === 'PATCH' || method === 'DELETE')) {
		const link = s.links.find((l) => l.token === match[1]);
		if (!link)
			return json(404, { error: 'Not found', code: 'share_link_not_found' }).then(() => true);
		if (method === 'DELETE') {
			log();
			s.links = s.links.filter((l) => l !== link);
			await route.fulfill({ status: 204 });
			return true;
		}
		const update = body();
		log(update);
		Object.assign(link, {
			expiresAt: update.expiresAt,
			maxViews: update.maxViews,
			allowDownload: update.allowDownload,
			allowUpload: update.allowUpload,
			hasPassword: update.password === null ? link.hasPassword : update.password !== ''
		});
		return ok(link);
	}

	return false;
};

export default handle;
