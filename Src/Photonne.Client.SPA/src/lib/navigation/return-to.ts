/**
 * Where to go after signing in. Only same-app paths are accepted: a value
 * like `//evil.example` or `https://…` would turn the login page into an open
 * redirect.
 */
export function safeReturnTo(value: string | null | undefined): string {
	if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
		return '/';
	}
	return value;
}
