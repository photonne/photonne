/** What a backup holds: settings only, plus the library, or everything with ML data. */
export type BackupLevel = 'config' | 'essential' | 'full';

export const BACKUP_LEVELS: readonly BackupLevel[] = ['config', 'essential', 'full'];

/** A backup file's summary, read in the browser before restoring it. */
export interface BackupSummary {
	version: string;
	createdAt: string | null;
	level: BackupLevel;
	users: number;
	folders: number;
	externalLibraries: number;
	assets: number;
	albums: number;
	people: number;
	faces: number;
	embeddings: number;
	ocrLines: number;
}

function count(root: Record<string, unknown>, key: string) {
	const value = root[key];
	return Array.isArray(value) ? value.length : 0;
}

/**
 * Reads the header of a backup document. Version 1.0 has no layer flags and
 * always means config + library without ML; 2.0 adds `includesMlData`; 3.0+
 * carries `includesConfig`, `includesLibrary` and `includesMlData`. Returns
 * null when the text isn't a Photonne backup.
 */
export function summarizeBackup(text: string): BackupSummary | null {
	let root: unknown;
	try {
		root = JSON.parse(text);
	} catch {
		return null;
	}
	if (typeof root !== 'object' || root === null || Array.isArray(root)) return null;
	const doc = root as Record<string, unknown>;
	if (!Array.isArray(doc.users)) return null;

	const version = typeof doc.version === 'string' ? doc.version : '?';
	const includesLibrary = version === '1.0' || doc.includesLibrary !== false;
	const includesMl = version !== '1.0' && includesLibrary && doc.includesMlData === true;

	return {
		version,
		createdAt: typeof doc.createdAt === 'string' ? doc.createdAt : null,
		level: !includesLibrary ? 'config' : includesMl ? 'full' : 'essential',
		users: count(doc, 'users'),
		folders: count(doc, 'folders'),
		externalLibraries: count(doc, 'externalLibraries'),
		assets: count(doc, 'assets'),
		albums: count(doc, 'albums'),
		people: count(doc, 'people'),
		faces: count(doc, 'faces'),
		embeddings: count(doc, 'assetEmbeddings'),
		ocrLines: count(doc, 'assetRecognizedTextLines')
	};
}

/** The file name the server suggests (Content-Disposition), if any. */
export function fileNameFromDisposition(header: string | null) {
	if (!header) return null;
	const encoded = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header);
	if (encoded) {
		try {
			return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, ''));
		} catch {
			// fall through to the plain form
		}
	}
	const plain = /filename=("?)([^";]+)\1/i.exec(header);
	return plain ? plain[2].trim() : null;
}
