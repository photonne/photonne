import { apiErrorCode } from '#lib/api/index.js';
import { m } from '#lib/paraglide/messages.js';

/** Codes whose server text already says exactly what is wrong (validation details). */
const EXPLAINED_BY_SERVER = new Set(['invalid_username', 'invalid_password', 'rename_failed']);

const KNOWN: Record<string, () => string> = {
	user_already_exists: m.admin_core_error_user_exists,
	email_already_exists: m.admin_core_error_email_exists,
	primary_admin_protected: m.admin_core_error_primary_protected,
	user_not_found: m.admin_core_error_user_not_found,
	target_not_admin: m.admin_core_error_target_not_admin,
	target_inactive: m.admin_core_error_target_inactive,
	directory_not_found: m.admin_core_error_directory_not_found,
	invalid_cron_schedule: m.admin_core_error_invalid_cron,
	library_not_found: m.admin_core_error_library_not_found,
	invalid_backup_file: m.admin_core_error_invalid_backup,
	backup_file_missing: m.admin_core_error_invalid_backup
};

/** A sentence for the user from an API error, or the fallback. */
export function errorText(error: unknown, fallback: () => string = m.admin_core_error_generic) {
	const code = apiErrorCode(error);
	if (code && KNOWN[code]) return KNOWN[code]();
	if (code && EXPLAINED_BY_SERVER.has(code)) return (error as { error: string }).error;
	return fallback();
}
