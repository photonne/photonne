import { m } from '#lib/paraglide/messages.js';
import type { UploadFailure } from './transport.js';

const failures: Record<UploadFailure, () => string> = {
	tooLarge: m.upload_failure_too_large,
	quota: m.upload_failure_quota,
	password: m.upload_failure_password,
	gone: m.upload_failure_gone,
	forbidden: m.upload_failure_forbidden,
	network: m.upload_failure_network,
	server: m.upload_failure_server
};

export function failureText(failure: UploadFailure) {
	return failures[failure]();
}
