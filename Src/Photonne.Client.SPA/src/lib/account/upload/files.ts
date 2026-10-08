/**
 * Which dropped or picked files are worth uploading, and how to get every
 * file out of a drop that may hold whole folders.
 */

// What the server indexes (MediaFileTypes.cs): anything else would be stored
// as an unknown type, so it is skipped up front.
const MEDIA_EXTENSIONS = new Set(
	[
		'jpg jpeg png bmp tiff tif gif webp',
		'heic heif',
		'raw cr2 cr3 nef arw dng orf rw2 pef raf srw',
		'mp4 avi mov mkv wmv flv webm m4v 3gp mpeg mpg 3g2 3gpp amv asf f4v m2v mp2 mpe mpv ogv qt vob'
	].flatMap((group) => group.split(' '))
);

/** The `accept` attribute for file inputs: the same set as the drop filter. */
export const MEDIA_ACCEPT = [...MEDIA_EXTENSIONS].map((extension) => `.${extension}`).join(',');

/** Upper bound per file, as in the Blazor and native clients (4 GB). */
export const MAX_FILE_BYTES = 4 * 1024 ** 3;

export function extensionOf(name: string): string {
	const dot = name.lastIndexOf('.');
	return dot <= 0 ? '' : name.slice(dot + 1).toLowerCase();
}

/** A photo or video the server can index; hidden files (.DS_Store, ._IMG…) never are. */
export function isMediaFile(file: { name: string }): boolean {
	return !file.name.startsWith('.') && MEDIA_EXTENSIONS.has(extensionOf(file.name));
}

/** A drag that carries files from the desktop (not text, not photos from the grid). */
export function isFileDrag(event: DragEvent): boolean {
	return event.dataTransfer?.types.includes('Files') ?? false;
}

/**
 * Every file in a drop, walking into dropped folders. Entries must be taken
 * from the DataTransfer synchronously, before the first await: the browser
 * empties it once the drop handler yields.
 */
export async function filesFromDrop(transfer: DataTransfer): Promise<File[]> {
	const entries: FileSystemEntry[] = [];
	const loose: File[] = [];
	for (const item of [...transfer.items]) {
		if (item.kind !== 'file') continue;
		const entry = item.webkitGetAsEntry?.();
		if (entry) entries.push(entry);
		else {
			const file = item.getAsFile();
			if (file) loose.push(file);
		}
	}
	if (entries.length === 0 && loose.length === 0) return [...transfer.files];

	const nested = await Promise.all(entries.map(filesOfEntry));
	return [...loose, ...nested.flat()];
}

async function filesOfEntry(entry: FileSystemEntry): Promise<File[]> {
	if (entry.isFile) {
		return new Promise((resolve) =>
			(entry as FileSystemFileEntry).file(
				(file) => resolve([file]),
				() => resolve([])
			)
		);
	}
	if (!entry.isDirectory) return [];
	const reader = (entry as FileSystemDirectoryEntry).createReader();
	const children: FileSystemEntry[] = [];
	// readEntries hands out a directory in chunks (100 in Chromium) until empty.
	for (;;) {
		const chunk = await new Promise<FileSystemEntry[]>((resolve) =>
			reader.readEntries(resolve, () => resolve([]))
		);
		if (chunk.length === 0) break;
		children.push(...chunk);
	}
	const nested = await Promise.all(children.map(filesOfEntry));
	return nested.flat();
}
