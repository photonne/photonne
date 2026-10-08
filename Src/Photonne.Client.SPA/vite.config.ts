import { paraglideVitePlugin } from '@inlang/paraglide-js';
import { defineConfig } from 'vitest/config';
import { playwright } from '@vitest/browser-playwright';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';

// The dev server proxies the API so the browser sees one origin: the session
// cookies (SameSite=Strict, Path=/api) then behave exactly as in production,
// where the server hosts the built SPA itself.
const apiTarget = process.env.PHOTONNE_API_URL ?? 'http://localhost:5030';

export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			// A single-page app: every route falls back to index.html and renders
			// in the browser (src/routes/+layout.ts turns SSR off).
			adapter: adapter({ fallback: 'index.html' })
		}),

		paraglideVitePlugin({
			project: './project.inlang',
			outdir: './src/lib/paraglide',
			emitTsDeclarations: true,
			// Behind a login there is no SEO to serve with /en/... URLs: the
			// language is the user's choice, else the browser's, else Spanish.
			strategy: ['localStorage', 'preferredLanguage', 'baseLocale']
		})
	],
	server: {
		proxy: {
			'/api': { target: apiTarget, changeOrigin: false },
			'/openapi': { target: apiTarget, changeOrigin: false }
		}
	},
	preview: {
		proxy: {
			'/api': { target: apiTarget, changeOrigin: false }
		}
	},
	test: {
		expect: { requireAssertions: true },
		projects: [
			{
				extends: './vite.config.ts',
				test: {
					name: 'client',
					browser: {
						enabled: true,
						provider: playwright(
							process.env.CHROMIUM_PATH
								? { launchOptions: { executablePath: process.env.CHROMIUM_PATH } }
								: {}
						),
						instances: [{ browser: 'chromium', headless: true }]
					},
					include: ['src/**/*.svelte.{test,spec}.{js,ts}'],
					exclude: ['src/lib/server/**']
				}
			},

			{
				extends: './vite.config.ts',
				test: {
					name: 'server',
					environment: 'node',
					include: ['src/**/*.{test,spec}.{js,ts}'],
					exclude: ['src/**/*.svelte.{test,spec}.{js,ts}']
				}
			}
		]
	}
});
