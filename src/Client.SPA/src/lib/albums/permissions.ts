/** The four flags albums and folders grant per user. */
export interface Grants {
	canRead: boolean;
	canWrite: boolean;
	canDelete: boolean;
	canManagePermissions: boolean;
}

/**
 * Access levels, each including the ones before it: see, add photos,
 * delete, and share with others. A grant set that isn't one of these
 * (made elsewhere) reads as `custom` and is kept until the user picks a level.
 */
export const accessLevels = ['view', 'contribute', 'edit', 'manage'] as const;
export type AccessLevel = (typeof accessLevels)[number];

export function grantsFor(level: AccessLevel): Grants {
	const rank = accessLevels.indexOf(level);
	return {
		canRead: true,
		canWrite: rank >= 1,
		canDelete: rank >= 2,
		canManagePermissions: rank >= 3
	};
}

export function levelOf(grants: Grants): AccessLevel | 'custom' {
	return (
		accessLevels.find((level) => {
			const expected = grantsFor(level);
			return (Object.keys(expected) as (keyof Grants)[]).every(
				(key) => expected[key] === grants[key]
			);
		}) ?? 'custom'
	);
}
