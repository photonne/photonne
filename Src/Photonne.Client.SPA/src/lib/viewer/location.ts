import { appHref } from '#lib/navigation/href.js';
// The map page's own model: the same tiles and the same ?lat=&lng=&z= link format.
import { centerParams } from '../../routes/(app)/map/map-model.js';

/** Zoom of the info panel's map and of the map page it links to. */
export const PLACE_ZOOM = 14;

/** A photo's position, when it has a usable one (0,0 is a camera without GPS fix). */
export function photoPosition(
	exif: { latitude?: number | null; longitude?: number | null } | null | undefined
) {
	const lat = exif?.latitude;
	const lng = exif?.longitude;
	if (lat == null || lng == null || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
	if (Math.abs(lat) > 90 || Math.abs(lng) > 180 || (lat === 0 && lng === 0)) return null;
	return { lat, lng };
}

/** The app's map centred on a place. */
export function mapHref(position: { lat: number; lng: number }, zoom = PLACE_ZOOM) {
	return `${appHref('/map')}?${new URLSearchParams(centerParams(position, zoom))}`;
}

/** The place on OpenStreetMap, for those who want directions. */
export function osmHref(position: { lat: number; lng: number }) {
	const { lat, lng } = position;
	return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=15/${lat}/${lng}`;
}
