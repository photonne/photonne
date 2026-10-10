/**
 * The server's own rules (AuthService.ValidatePassword,
 * UserStorageService.ValidateUsername), checked as the user types so the form
 * says what is missing before a round trip.
 */

export type PasswordRule = 'length' | 'upper' | 'lower' | 'digit' | 'symbol';

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;

export function passwordRules(password: string): Record<PasswordRule, boolean> {
	const chars = [...password];
	return {
		length: chars.length >= PASSWORD_MIN && chars.length <= PASSWORD_MAX,
		upper: chars.some((c) => c !== c.toLowerCase()),
		lower: chars.some((c) => c !== c.toUpperCase()),
		digit: /\p{Nd}/u.test(password),
		symbol: chars.some((c) => !/[\p{L}\p{Nd}]/u.test(c))
	};
}

export function isStrongPassword(password: string): boolean {
	return Object.values(passwordRules(password)).every(Boolean);
}

const USERNAME = /^[a-zA-Z0-9._-]{1,64}$/;

export function isValidUsername(username: string): boolean {
	return USERNAME.test(username);
}

/** Loose check: the server is the judge; this catches typos early. */
export function isValidEmail(email: string): boolean {
	return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}
