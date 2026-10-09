import { defineConfig, devices } from '@playwright/test';

// E2E_PORT lets several checkouts run their e2e at the same time.
const port = Number(process.env.E2E_PORT ?? 4173);

// End-to-end tests run against the production build. The API is faked per
// test (e2e/fake-api.ts), so no server is needed.
export default defineConfig({
	testDir: 'e2e',
	testMatch: '**/*.e2e.ts',
	fullyParallel: true,
	forbidOnly: !!process.env.CI,
	reporter: process.env.CI ? 'github' : 'list',
	use: {
		baseURL: `http://localhost:${port}`,
		trace: 'retain-on-failure',
		// The app's service worker would sit between the page and the faked API.
		serviceWorkers: 'block'
	},
	projects: [
		{
			name: 'chromium',
			testIgnore: '**/*.lan.e2e.ts',
			use: {
				...devices['Desktop Chrome'],
				// For machines with a Chromium already installed (e.g. a sandbox
				// that can't download Playwright's own build).
				launchOptions: process.env.CHROMIUM_PATH
					? { executablePath: process.env.CHROMIUM_PATH }
					: {}
			}
		},
		{
			// A self-hosted server is often opened as http://<lan-name>:port: not a
			// secure context, so APIs such as crypto.randomUUID or the clipboard
			// are missing. localhost doesn't show that, so these tests use a name
			// that resolves to it without being it.
			name: 'lan-http',
			testMatch: '**/*.lan.e2e.ts',
			use: {
				...devices['Desktop Chrome'],
				baseURL: `http://photonne.lan:${port}`,
				launchOptions: {
					args: ['--host-resolver-rules=MAP photonne.lan 127.0.0.1'],
					...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {})
				}
			}
		}
	],
	webServer: {
		command: `npm run build && npm run preview -- --port ${port} --strictPort`,
		port,
		// Only reuse a server explicitly started for this checkout (E2E_REUSE=1):
		// another checkout's preview on the same port would be tested silently.
		reuseExistingServer: process.env.E2E_REUSE === '1'
	}
});
