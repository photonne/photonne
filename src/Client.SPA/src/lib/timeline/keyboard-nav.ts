export type Direction = 'left' | 'right' | 'up' | 'down';

/**
 * Focus movement in a justified grid given as rows of ids in visual order.
 * Left/right walk the flat order (wrapping across rows); up/down move to the
 * adjacent row keeping the position within the row (clamped), the usual
 * gallery behaviour. Same rules as the Blazor workspace (GridKeyboardNav.cs).
 */
export function moveFocus(
	rows: readonly (readonly string[])[],
	current: string | null,
	direction: Direction
): string | null {
	if (rows.length === 0) return null;
	const position = current === null ? null : locate(rows, current);
	if (position === null) return rows[0][0] ?? null;

	const [r, c] = position;
	switch (direction) {
		case 'left':
			if (c > 0) return rows[r][c - 1];
			return r > 0 ? rows[r - 1].at(-1)! : current;
		case 'right':
			if (c < rows[r].length - 1) return rows[r][c + 1];
			return r < rows.length - 1 ? rows[r + 1][0] : current;
		case 'up':
			return r > 0 ? rows[r - 1][Math.min(c, rows[r - 1].length - 1)] : current;
		case 'down':
			return r < rows.length - 1 ? rows[r + 1][Math.min(c, rows[r + 1].length - 1)] : current;
	}
}

function locate(rows: readonly (readonly string[])[], id: string): [number, number] | null {
	for (let r = 0; r < rows.length; r++) {
		const c = rows[r].indexOf(id);
		if (c >= 0) return [r, c];
	}
	return null;
}
