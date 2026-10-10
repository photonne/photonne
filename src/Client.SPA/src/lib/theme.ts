export type Theme = 'system' | 'light' | 'dark';

const KEY = 'photonne.theme';

/** The saved choice; 'system' when none (or storage is unavailable). */
export function getTheme(): Theme {
	try {
		const value = localStorage.getItem(KEY);
		return value === 'light' || value === 'dark' ? value : 'system';
	} catch {
		return 'system';
	}
}

/**
 * Saves the choice and applies it at once. app.html applies the saved value
 * before the first paint, so a reload never flashes the other theme.
 */
export function setTheme(theme: Theme) {
	try {
		if (theme === 'system') localStorage.removeItem(KEY);
		else localStorage.setItem(KEY, theme);
	} catch {
		// Applied for this page only.
	}
	applyTheme(theme);
}

export function applyTheme(theme: Theme) {
	if (theme === 'system') delete document.documentElement.dataset.theme;
	else document.documentElement.dataset.theme = theme;
}
