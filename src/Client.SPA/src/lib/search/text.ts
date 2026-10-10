/** Lower case without accents, so "jose" finds "José". */
export function fold(text: string) {
	return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

/** Whether `text` contains `needle`, ignoring case and accents. */
export function matches(text: string, needle: string) {
	return fold(text).includes(fold(needle.trim()));
}

/** "beach_house" → "Beach house": a model label made readable. */
export function labelText(label: string) {
	const words = label.replace(/[_-]+/g, ' ').trim();
	return words.charAt(0).toUpperCase() + words.slice(1);
}

/** The last segment of a path: "/assets/users/ana/Viajes" → "Viajes". */
export function lastSegment(path: string) {
	return path.replace(/\/+$/, '').split('/').at(-1) || path;
}

/** The folder part of a file path: "/a/b/c.jpg" → "/a/b". */
export function directoryOf(path: string) {
	const index = path.lastIndexOf('/');
	return index > 0 ? path.slice(0, index) : path;
}
