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

/** --color-bg of each theme (app.css), for the browser's own bar (theme-color). */
const BAR_COLORS = { light: '#f8f9fa', dark: '#0a0a0c' } as const;

export function applyTheme(theme: Theme) {
	if (theme === 'system') delete document.documentElement.dataset.theme;
	else document.documentElement.dataset.theme = theme;
	// app.html has one theme-color per system scheme: a chosen theme gives
	// both its colour; 'system' gives each its own back.
	for (const meta of document.querySelectorAll<HTMLMetaElement>('meta[name="theme-color"]')) {
		const scheme = meta.media.includes('dark') ? 'dark' : 'light';
		meta.content = BAR_COLORS[theme === 'system' ? scheme : theme];
	}
}
