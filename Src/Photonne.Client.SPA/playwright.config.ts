import { defineConfig, devices } from '@playwright/test';

// End-to-end tests run against the production build. The API is faked per
// test (e2e/fake-api.ts), so no server is needed.
export default defineConfig({
	testDir: 'e2e',
	testMatch: '**/*.e2e.ts',
	fullyParallel: true,
	forbidOnly: !!process.env.CI,
	reporter: process.env.CI ? 'github' : 'list',
	use: { baseURL: 'http://localhost:4173', trace: 'retain-on-failure' },
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
		command: 'npm run build && npm run preview -- --port 4173 --strictPort',
		port: 4173,
		reuseExistingServer: !process.env.CI
	}
});
