import { describe, expect, it } from 'vitest';
import {
	canDelete,
	canPromote,
	displayName,
	formToQuota,
	GIB,
	initials,
	isStrongPassword,
	isValidUsername,
	matchesQuery,
	nextSort,
	passwordChecks,
	quotaShare,
	quotaToForm,
	sortUsers,
	type AdminUser
} from './users.js';

function user(overrides: Partial<AdminUser>): AdminUser {
	return {
		id: overrides.username ?? 'id',
		username: 'user',
		email: 'user@test',
		role: 'User',
		isActive: true,
		isPrimaryAdmin: false,
		createdAt: '2026-01-01T00:00:00Z',
		lastLoginAt: null,
		usedBytes: null,
		...overrides
	};
}

describe('users', () => {
	it('names a user by real name, else username', () => {
		expect(displayName(user({ firstName: 'Ana', lastName: 'García' }))).toBe('Ana García');
		expect(displayName(user({ firstName: ' ', username: 'ana' }))).toBe('ana');
		expect(initials(user({ firstName: 'Ana', lastName: 'García' }))).toBe('AG');
		expect(initials(user({ firstName: 'Ana' }))).toBe('AN');
		expect(initials(user({ username: 'jo' }))).toBe('JO');
	});

	it('searches every word across username, email and name, ignoring accents', () => {
		const jose = user({
			username: 'jperez',
			firstName: 'José',
			lastName: 'Pérez',
			email: 'j@x.es'
		});

		expect(matchesQuery(jose, '')).toBe(true);
		expect(matchesQuery(jose, 'jose perez')).toBe(true);
		expect(matchesQuery(jose, 'X.ES')).toBe(true);
		expect(matchesQuery(jose, 'jose lopez')).toBe(false);
	});

	it('sorts by column with a stable name tie-break', () => {
		const users = [
			user({ username: 'carla', usedBytes: 5 }),
			user({ username: 'ana', usedBytes: 5, role: 'Admin' }),
			user({ username: 'bea', usedBytes: 9, isPrimaryAdmin: true, role: 'Admin' })
		];

		expect(sortUsers(users, 'name', 'asc').map((u) => u.username)).toEqual(['ana', 'bea', 'carla']);
		expect(sortUsers(users, 'usage', 'desc').map((u) => u.username)).toEqual([
			'bea',
			'ana',
			'carla'
		]);
		expect(sortUsers(users, 'role', 'asc').map((u) => u.username)).toEqual(['bea', 'ana', 'carla']);
	});

	it('flips the current column and starts dates and sizes newest first', () => {
		expect(nextSort({ key: 'name', direction: 'asc' }, 'name')).toEqual({
			key: 'name',
			direction: 'desc'
		});
		expect(nextSort({ key: 'name', direction: 'asc' }, 'usage').direction).toBe('desc');
		expect(nextSort({ key: 'usage', direction: 'desc' }, 'email').direction).toBe('asc');
	});

	it('maps quotas to the form and back', () => {
		expect(quotaToForm(null)).toEqual({ choice: 'unlimited', customGb: 10 });
		expect(quotaToForm(5 * GIB).choice).toBe('5');
		expect(quotaToForm(7 * GIB)).toEqual({ choice: 'custom', customGb: 7 });

		expect(formToQuota({ choice: 'unlimited', customGb: 3 })).toBeNull();
		expect(formToQuota({ choice: '50', customGb: 3 })).toBe(50 * GIB);
		expect(formToQuota({ choice: 'custom', customGb: 3 })).toBe(3 * GIB);
		expect(formToQuota({ choice: 'custom', customGb: 0 })).toBeNull();
	});

	it('measures quota use', () => {
		expect(quotaShare(5, null)).toBeNull();
		expect(quotaShare(null, 10)).toBeNull();
		expect(quotaShare(5, 10)).toBe(0.5);
		expect(quotaShare(20, 10)).toBe(1);
	});

	it('checks usernames and passwords like the server', () => {
		expect(isValidUsername('ana.garcia-2_b')).toBe(true);
		expect(isValidUsername('ana garcía')).toBe(false);
		expect(isValidUsername('a'.repeat(65))).toBe(false);

		expect(passwordChecks('abc')).toEqual({
			length: false,
			upper: false,
			lower: true,
			digit: false,
			symbol: false
		});
		expect(isStrongPassword('Secreto-2026')).toBe(true);
		expect(isStrongPassword('Secreto2026')).toBe(false);
	});

	it('protects the primary admin and the signed-in admin', () => {
		const me = user({ id: 'me', role: 'Admin', isPrimaryAdmin: true });
		const admin = user({ id: 'other', role: 'Admin' });
		const plain = user({ id: 'plain' });

		expect(canDelete(me, me)).toBe(false);
		expect(canDelete(admin, me)).toBe(true);
		expect(canDelete(me, admin)).toBe(false);

		expect(canPromote(admin, me)).toBe(true);
		expect(canPromote(plain, me)).toBe(false);
		expect(canPromote({ ...admin, isActive: false }, me)).toBe(false);
		expect(canPromote(admin, { ...me, isPrimaryAdmin: false })).toBe(false);
	});
});
