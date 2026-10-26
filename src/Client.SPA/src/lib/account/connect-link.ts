/**
 * The link the mobile app opens to set up its server: `photonne://connect`
 * with the server's address. Shown as a QR code in Ajustes → App móvil, so
 * nobody has to type an address on a phone keyboard.
 */
export function connectLink(server: string): string {
	const origin = server.trim().replace(/\/+$/, '');
	return `photonne://connect?server=${encodeURIComponent(origin)}`;
}
