import { describe, expect, it } from 'vitest';
import { legacyPath, legacyTarget } from './legacy.js';

describe('legacyPath', () => {
	it.each([
		['/fotos', '/'],
		['/albumes/', '/albums'],
		['/albumes/3f1c', '/albums/3f1c'],
		['/carpetas/9a', '/folders/9a'],
		['/papelera', '/trash'],
		['/shared-trash', '/trash?scope=shared'],
		['/admin/system/tasks/thumbnails', '/admin/tasks'],
		['/admin/system/tasks', '/admin/tasks'],
		['/admin/system/tasks/duplicates', '/admin/maintenance/duplicates'],
		['/admin/settings/face-recognition', '/admin/settings/faces']
	])('%s → %s', (path, target) => {
		expect(legacyPath(path)).toBe(target);
	});

	it.each(['/', '/albums', '/albums/3f1c', '/admin/tasks', '/admin/settings/server'])(
		'leaves %s alone',
		(path) => {
			expect(legacyPath(path)).toBeNull();
		}
	);
});

describe('legacyTarget', () => {
	it('keeps the query, merged into the target one', () => {
		expect(legacyTarget(new URL('http://x/buscar?q=playa'))).toBe('/search?q=playa');
		expect(legacyTarget(new URL('http://x/shared-trash?from=n'))).toBe(
			'/trash?scope=shared&from=n'
		);
		expect(legacyTarget(new URL('http://x/admin/enrichment-failures?type=Exif'))).toBe(
			'/admin/tasks/failures?type=Exif'
		);
	});
});
