/**
 * Server settings are plain key/value strings (`/api/settings?key=…`). The
 * server never rejects a value: it clamps it, or falls back to its default,
 * when it reads it, without telling anybody. So the form is the only place
 * where an admin can learn that 500 is not a JPEG quality, and the rules
 * below mirror what the server accepts.
 */

export type FieldKind =
	'bool' | 'int' | 'decimal' | 'text' | 'url' | 'secret' | 'select' | 'time' | 'timezone';

export interface FieldOption {
	value: string;
	label: () => string;
}

export interface FieldDef {
	key: string;
	kind: FieldKind;
	/** What the server uses when the key was never stored (it answers ""). */
	default: string;
	label: () => string;
	hint?: () => string;
	/** Unit after a number ("MB", "min"…). */
	unit?: () => string;
	min?: number;
	max?: number;
	step?: number;
	/** Number with a slider next to it, for bounded values worth feeling out. */
	slider?: boolean;
	options?: FieldOption[];
	placeholder?: string;
	/** Hidden unless this bool field is on (e.g. a mode that only means something when enabled). */
	visibleWhen?: string;
	/** Empty is not allowed (the server would fall back to its own value). */
	required?: boolean;
	/** A caution shown while the field holds this value. */
	warnWhen?: { value: string; message: () => string };
}

export type FieldError = 'required' | 'notNumber' | 'range' | 'time' | 'url' | 'cross';

export type Values = Record<string, string>;

export function isOn(value: string | undefined) {
	return value?.trim().toLowerCase() === 'true';
}

/** "0,5" → "0.5": the decimal comma is accepted while typing, the server wants a dot. */
export function normalizeDecimal(raw: string) {
	return raw.trim().replace(',', '.');
}

const INT = /^-?\d+$/;
const DECIMAL = /^-?(\d+\.?\d*|\.\d+)$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Parses a number field's value, or null when it isn't one. */
export function parseNumber(field: Pick<FieldDef, 'kind'>, raw: string): number | null {
	const value = field.kind === 'decimal' ? normalizeDecimal(raw) : raw.trim();
	if (!(field.kind === 'decimal' ? DECIMAL : INT).test(value)) return null;
	return Number(value);
}

export function validateField(field: FieldDef, raw: string): FieldError | null {
	const value = raw.trim();
	switch (field.kind) {
		case 'int':
		case 'decimal': {
			if (value === '') return 'required';
			const number = parseNumber(field, value);
			if (number === null) return 'notNumber';
			if (
				(field.min !== undefined && number < field.min) ||
				(field.max !== undefined && number > field.max)
			)
				return 'range';
			return null;
		}
		case 'time':
			return TIME.test(value) ? null : 'time';
		case 'url':
			if (value === '') return field.required ? 'required' : null;
			try {
				const url = new URL(value);
				return url.protocol === 'http:' || url.protocol === 'https:' ? null : 'url';
			} catch {
				return 'url';
			}
		default:
			return field.required && value === '' ? 'required' : null;
	}
}

/** Every field in error, plus the keys a section's cross-field rule blames. */
export function validate(
	fields: readonly FieldDef[],
	values: Values,
	crossCheck?: (values: Values) => string[]
): Record<string, FieldError> {
	const errors: Record<string, FieldError> = {};
	for (const field of fields) {
		const error = validateField(field, values[field.key] ?? '');
		if (error) errors[field.key] = error;
	}
	for (const key of crossCheck?.(values) ?? []) errors[key] ??= 'cross';
	return errors;
}

/** The stored values, with the server's default for every key never stored. */
export function withDefaults(fields: readonly FieldDef[], fetched: Values): Values {
	const values: Values = {};
	for (const field of fields) {
		const stored = fetched[field.key];
		values[field.key] = stored !== undefined && stored.trim() !== '' ? stored : field.default;
	}
	return values;
}

/** What a field sends: trimmed, decimals with a dot, booleans spelled the server's way. */
export function toStored(field: FieldDef, raw: string) {
	if (field.kind === 'bool') return isOn(raw) ? 'true' : 'false';
	if (field.kind === 'decimal') return normalizeDecimal(raw);
	if (field.kind === 'secret') return raw;
	return raw.trim();
}

/** The [key, value] pairs to save: the ones that differ from what the server has. */
export function changes(fields: readonly FieldDef[], original: Values, current: Values) {
	const changed: [string, string][] = [];
	for (const field of fields) {
		const next = toStored(field, current[field.key] ?? '');
		if (next !== toStored(field, original[field.key] ?? '')) changed.push([field.key, next]);
	}
	return changed;
}
