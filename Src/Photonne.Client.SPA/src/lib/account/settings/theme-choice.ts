// Same contract as the shared `src/lib/theme.ts` (commit 8a03d9d: localStorage
// key `photonne.theme`, `data-theme` on <html>, honoured by app.css and
// applied before first paint by app.html). That commit isn't on this branch
// yet; once it is, this file goes and the Appearance page imports
// `#lib/theme.js` instead — the names and behaviour are identical.

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

/** Saves the choice and applies it at once. */
export function setTheme(theme: Theme) {
	try {
		if (theme === 'system') localStorage.removeItem(KEY);
		else localStorage.setItem(KEY, theme);
	} catch {
		// Applied for this page only.
	}
	if (theme === 'system') delete document.documentElement.dataset.theme;
	else document.documentElement.dataset.theme = theme;
}
