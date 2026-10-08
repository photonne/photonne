import { describe, expect, it } from 'vitest';
import { fileNameFromDisposition, summarizeBackup } from './backup.js';

describe('backup', () => {
	it('summarises a full v3 backup', () => {
		const text = JSON.stringify({
			version: '3.0',
			createdAt: '2026-10-01T10:00:00Z',
			includesConfig: true,
			includesLibrary: true,
			includesMlData: true,
			users: [{}, {}],
			folders: [{}],
			externalLibraries: [],
			assets: [{}, {}, {}],
			albums: [{}],
			people: [{}],
			faces: [{}, {}],
			assetEmbeddings: [{}],
			assetRecognizedTextLines: []
		});

		expect(summarizeBackup(text)).toEqual({
			version: '3.0',
			createdAt: '2026-10-01T10:00:00Z',
			level: 'full',
			users: 2,
			folders: 1,
			externalLibraries: 0,
			assets: 3,
			albums: 1,
			people: 1,
			faces: 2,
			embeddings: 1,
			ocrLines: 0
		});
	});

	it('tells the levels apart, old versions included', () => {
		const level = (doc: object) => summarizeBackup(JSON.stringify({ users: [], ...doc }))?.level;

		expect(level({ version: '3.0', includesLibrary: false })).toBe('config');
		expect(level({ version: '3.0', includesLibrary: true, includesMlData: false })).toBe(
			'essential'
		);
		expect(level({ version: '2.0', includesMlData: true })).toBe('full');
		expect(level({ version: '1.0', includesMlData: true })).toBe('essential');
	});

	it('rejects what is not a backup', () => {
		expect(summarizeBackup('not json')).toBeNull();
		expect(summarizeBackup('[1,2]')).toBeNull();
		expect(summarizeBackup('{"hello":1}')).toBeNull();
	});

	it('reads the suggested file name', () => {
		expect(fileNameFromDisposition(null)).toBeNull();
		expect(fileNameFromDisposition('attachment; filename=photonne_backup_full_20261001.json')).toBe(
			'photonne_backup_full_20261001.json'
		);
		expect(
			fileNameFromDisposition('attachment; filename="a.json"; filename*=UTF-8\'\'copia%20b.json')
		).toBe('copia b.json');
	});
});
