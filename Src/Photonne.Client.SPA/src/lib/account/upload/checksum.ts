/**
 * Files above this are not hashed in the browser: SubtleCrypto needs the whole
 * file in memory. The server still deduplicates them on arrival.
 */
export const MAX_HASH_BYTES = 512 * 1024 ** 2;

/** Lowercase hex, the format the server stores (FileHashService). */
export function toHex(buffer: ArrayBuffer): string {
	return [...new Uint8Array(buffer)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

/** SHA-256 of a file, or null when it is too big to hash here. */
export async function sha256Hex(file: Blob): Promise<string | null> {
	if (file.size > MAX_HASH_BYTES || !globalThis.crypto?.subtle) return null;
	const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
	return toHex(digest);
}
