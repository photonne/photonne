const KEY = 'photonne.deviceId';

/**
 * A stable id for this browser. The server keeps one refresh token per user
 * and device, so signing in here doesn't sign out the phone. Kept in
 * localStorage; if storage is unavailable (private mode, blocked) the id lives
 * for the page only, which just means a new device row per visit.
 */
export function getDeviceId(storage: Pick<Storage, 'getItem' | 'setItem'> | null = safeStorage()) {
	const existing = read(storage);
	if (existing) return existing;

	const created = `web-${crypto.randomUUID()}`;
	try {
		storage?.setItem(KEY, created);
	} catch {
		// Storage full or blocked: keep the in-memory id.
	}
	memory = created;
	return created;
}

let memory: string | null = null;

function read(storage: Pick<Storage, 'getItem'> | null) {
	try {
		return storage?.getItem(KEY) ?? memory;
	} catch {
		return memory;
	}
}

function safeStorage() {
	try {
		return globalThis.localStorage ?? null;
	} catch {
		return null;
	}
}
