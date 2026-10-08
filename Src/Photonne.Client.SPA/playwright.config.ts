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
	use: { baseURL: `http://localhost:${port}`, trace: 'retain-on-failure' },
	projects: [
		{
			name: 'chromium',
			use: {
				...devices['Desktop Chrome'],
				// For machines with a Chromium already installed (e.g. a sandbox
				// that can't download Playwright's own build).
				launchOptions: process.env.CHROMIUM_PATH
					? { executablePath: process.env.CHROMIUM_PATH }
					: {}
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
