import type { UserDto } from '#lib/api/index.js';

/** The user list as the admin table works with it: the DTO plus storage used. */
export interface AdminUser extends UserDto {
	id: string;
	username: string;
	/** Bytes of photos and videos (from the admin stats), null when unknown. */
	usedBytes: number | null;
}

export type UserSortKey = 'name' | 'email' | 'role' | 'status' | 'usage' | 'lastLogin' | 'created';
export type SortDirection = 'asc' | 'desc';

export const GIB = 1024 ** 3;

/** "Ana García", or the username when there is no real name. */
export function displayName(user: Pick<UserDto, 'firstName' | 'lastName' | 'username'>) {
	const full = [user.firstName, user.lastName]
		.map((part) => part?.trim())
		.filter(Boolean)
		.join(' ');
	return full || user.username || '';
}

/** Two letters for the avatar: initials of the real name, else of the username. */
export function initials(user: Pick<UserDto, 'firstName' | 'lastName' | 'username'>) {
	const first = user.firstName?.trim();
	const last = user.lastName?.trim();
	if (first && last) return (first[0] + last[0]).toUpperCase();
	if (first) return first.slice(0, 2).toUpperCase();
	return (user.username ?? '').slice(0, 2).toUpperCase();
}

/** Lower case without accents, so "jose" finds "José". */
export function fold(text: string) {
	return text
		.normalize('NFD')
		.replace(/\p{Diacritic}/gu, '')
		.toLowerCase();
}

/** Every word of the query appears in the username, email or real name. */
export function matchesQuery(user: UserDto, query: string) {
	const words = fold(query).split(/\s+/).filter(Boolean);
	if (words.length === 0) return true;
	const haystack = fold(
		[user.username, user.email, user.firstName, user.lastName].filter(Boolean).join(' ')
	);
	return words.every((word) => haystack.includes(word));
}

function sortValue(user: AdminUser, key: UserSortKey): string | number {
	switch (key) {
		case 'name':
			return fold(displayName(user));
		case 'email':
			return fold(user.email ?? '');
		case 'role':
			// Primary admin first, then admins, then users.
			return user.isPrimaryAdmin ? 0 : user.role === 'Admin' ? 1 : 2;
		case 'status':
			return user.isActive ? 0 : 1;
		case 'usage':
			return user.usedBytes ?? -1;
		case 'lastLogin':
			return user.lastLoginAt ? Date.parse(user.lastLoginAt) : 0;
		case 'created':
			return user.createdAt ? Date.parse(user.createdAt) : 0;
	}
}

/** A sorted copy; ties fall back to the display name so the order is stable. */
export function sortUsers(users: readonly AdminUser[], key: UserSortKey, direction: SortDirection) {
	const sign = direction === 'asc' ? 1 : -1;
	return [...users].sort((a, b) => {
		const x = sortValue(a, key);
		const y = sortValue(b, key);
		if (x < y) return -sign;
		if (x > y) return sign;
		return fold(displayName(a)).localeCompare(fold(displayName(b)));
	});
}

/** Clicking the current column flips it; a new column starts in its natural order. */
export function nextSort(
	current: { key: UserSortKey; direction: SortDirection },
	key: UserSortKey
): { key: UserSortKey; direction: SortDirection } {
	if (current.key === key) {
		return { key, direction: current.direction === 'asc' ? 'desc' : 'asc' };
	}
	// Numbers and dates read best biggest/newest first.
	const descending: UserSortKey[] = ['usage', 'lastLogin', 'created'];
	return { key, direction: descending.includes(key) ? 'desc' : 'asc' };
}

// ── Storage quota ─────────────────────────────────────────────────────────

export const QUOTA_PRESETS_GB = [1, 5, 10, 50, 100] as const;

/** The quota as the form shows it: unlimited, one of the presets, or a custom size. */
export interface QuotaForm {
	choice: 'unlimited' | 'custom' | `${number}`;
	customGb: number;
}

export function quotaToForm(bytes: number | null | undefined): QuotaForm {
	if (bytes === null || bytes === undefined || bytes <= 0) {
		return { choice: 'unlimited', customGb: 10 };
	}
	const gb = bytes / GIB;
	if ((QUOTA_PRESETS_GB as readonly number[]).includes(gb)) {
		return { choice: `${gb}`, customGb: gb };
	}
	return { choice: 'custom', customGb: Math.max(1, Math.round(gb)) };
}

/** Bytes for the API, or null for no limit. */
export function formToQuota(form: QuotaForm): number | null {
	if (form.choice === 'unlimited') return null;
	const gb = form.choice === 'custom' ? form.customGb : Number(form.choice);
	return Number.isFinite(gb) && gb > 0 ? Math.round(gb * GIB) : null;
}

/** 0–1 share of the quota in use, or null when there is no quota. */
export function quotaShare(used: number | null, quota: number | null | undefined) {
	if (!quota || quota <= 0 || used === null) return null;
	return Math.min(1, used / quota);
}

// ── Validation (mirrors the server, to explain before sending) ────────────

const USERNAME = /^[a-zA-Z0-9._-]{1,64}$/;

export function isValidUsername(name: string) {
	return USERNAME.test(name);
}

export function isValidEmail(email: string) {
	return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

export interface PasswordChecks {
	length: boolean;
	upper: boolean;
	lower: boolean;
	digit: boolean;
	symbol: boolean;
}

/** The server's password rules, one by one, for a live checklist. */
export function passwordChecks(password: string): PasswordChecks {
	return {
		length: password.length >= 8 && password.length <= 128,
		upper: /\p{Lu}/u.test(password),
		lower: /\p{Ll}/u.test(password),
		digit: /\p{Nd}/u.test(password),
		symbol: /[^\p{L}\p{Nd}]/u.test(password)
	};
}

export function isStrongPassword(password: string) {
	return Object.values(passwordChecks(password)).every(Boolean);
}

// ── What the signed-in admin may do to a user ─────────────────────────────

type Who = Pick<UserDto, 'id' | 'role' | 'isActive' | 'isPrimaryAdmin'>;

/** The primary admin can't be deleted, and nobody deletes themselves from here. */
export function canDelete(target: Who, me: Who | null) {
	return !target.isPrimaryAdmin && target.id !== me?.id;
}

/** The primary admin's role and active flag are protected by the server. */
export function canChangeRoleOrStatus(target: Who, me: Who | null) {
	return !target.isPrimaryAdmin && target.id !== me?.id;
}

/** Only the primary admin hands the role over, and only to an active admin. */
export function canPromote(target: Who, me: Who | null) {
	return (
		!!me?.isPrimaryAdmin &&
		target.id !== me.id &&
		target.role === 'Admin' &&
		!!target.isActive &&
		!target.isPrimaryAdmin
	);
}
