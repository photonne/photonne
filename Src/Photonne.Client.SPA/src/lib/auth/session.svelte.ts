import {
	apiErrorCode,
	getCurrentUser,
	login as loginRequest,
	logout as logoutRequest,
	refreshToken,
	type UserDto
} from '#lib/api/index.js';
import { getDeviceId } from './device-id.js';

/**
 * `unreachable`: the server couldn't be reached while restoring, so it is not
 * known whether the session is still valid; the app offers a retry instead of
 * sending the user to the login form.
 */
export type SessionStatus = 'restoring' | 'signedIn' | 'signedOut' | 'unreachable';

export type LoginFailure = 'invalidCredentials' | 'rateLimited' | 'network' | 'server';

/**
 * Who is signed in, for the whole app.
 *
 * The access token lives only in memory; the refresh token lives in an
 * HttpOnly cookie the page can't read (`refreshTokenInCookie`), so a script
 * injected into the page can use a session while it is open but can't carry
 * one off. On load the session is restored by asking for a fresh access token
 * with that cookie. Login and refresh also set the media cookie that lets
 * `<img>` and `<video>` load photos.
 */
export class Session {
	status = $state<SessionStatus>('restoring');
	user = $state<UserDto | null>(null);
	isAdmin = $derived(this.user?.role === 'Admin');

	#accessToken: string | null = null;
	#refreshing: Promise<string | null> | null = null;

	get accessToken() {
		return this.#accessToken;
	}

	/** At startup (and on retry): signed in if the refresh cookie is still valid. */
	async restore() {
		this.status = 'restoring';
		const token = await this.refresh();
		if (token === null) {
			if (this.status === 'restoring') this.status = 'unreachable';
			return;
		}

		const { data, response } = await getCurrentUser();
		if (data) {
			this.user = data;
			this.status = 'signedIn';
		} else if (unreachable(response)) {
			this.status = 'unreachable';
		} else {
			this.#signOutLocally();
		}
	}

	async login(username: string, password: string): Promise<LoginFailure | null> {
		const { data, response, error } = await loginRequest({
			body: { username, password, deviceId: getDeviceId(), refreshTokenInCookie: true }
		});

		// No response at all: the request never reached the server.
		if (!response) return 'network';
		if (data?.token) {
			this.#accessToken = data.token;
			this.user = data.user;
			this.status = 'signedIn';
			return null;
		}
		if (response.status === 401 || apiErrorCode(error) === 'credentials_required') {
			return 'invalidCredentials';
		}
		if (response.status === 429) return 'rateLimited';
		return 'server';
	}

	async logout() {
		try {
			await logoutRequest();
		} finally {
			this.#signOutLocally();
		}
	}

	/**
	 * A fresh access token from the refresh cookie, or null when the session
	 * is over. Concurrent callers (several requests hitting 401 at once) share
	 * one request: the server rotates the refresh token, so a second parallel
	 * refresh would present a token that no longer exists.
	 */
	refresh(): Promise<string | null> {
		this.#refreshing ??= this.#doRefresh().finally(() => (this.#refreshing = null));
		return this.#refreshing;
	}

	async #doRefresh(): Promise<string | null> {
		const { data, response } = await refreshToken({ body: { deviceId: getDeviceId() } });
		if (data?.token) {
			this.#accessToken = data.token;
			return data.token;
		}
		// The server couldn't be reached. The session may well still be valid,
		// so it is kept; the request that needed it fails alone.
		if (!unreachable(response)) this.#signOutLocally();
		return null;
	}

	#signOutLocally() {
		this.#accessToken = null;
		this.user = null;
		this.status = 'signedOut';
	}
}

export const session = new Session();

/**
 * No answer from the server: no response at all, or a reverse proxy in front
 * of it saying it is down (Bad Gateway, Service Unavailable, Gateway Timeout).
 */
function unreachable(response: Response | undefined) {
	return !response || [502, 503, 504].includes(response.status);
}
