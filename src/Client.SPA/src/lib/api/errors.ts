import type { ApiError } from './generated';

function isApiError(value: unknown): value is ApiError {
	return (
		typeof value === 'object' &&
		value !== null &&
		typeof (value as ApiError).error === 'string' &&
		typeof (value as ApiError).code === 'string'
	);
}

/**
 * The stable `code` of a 4xx the server answered on purpose (`ApiError`), or
 * null for anything else (network failure, 500, unexpected body).
 */
export function apiErrorCode(error: unknown): string | null {
	return isApiError(error) ? error.code : null;
}
