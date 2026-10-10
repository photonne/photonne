import { expect, test } from '@playwright/test';

test.use({ locale: 'es-ES', serviceWorkers: 'allow' });

// No faked API here: Playwright bypasses service workers while a page has
// routes, and this test is about the worker. Without a server behind the
// preview's proxy the app shows its "can't reach the server" screen, online
// or not.
test('is installable and opens its shell without a connection', async ({ page, context }) => {
	await page.goto('/');
	await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();

	const manifest = await page.locator('link[rel="manifest"]').getAttribute('href');
	const response = await page.request.get(manifest!);
	expect((await response.json()).start_url).toBe('/');

	// The worker installs, takes over, and keeps the shell for offline use.
	const controlled = await page.evaluate(async () => {
		await navigator.serviceWorker.ready;
		if (!navigator.serviceWorker.controller) {
			await new Promise((resolve) =>
				navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true })
			);
		}
		return navigator.serviceWorker.controller !== null;
	});
	expect(controlled).toBe(true);

	await context.setOffline(true);
	await page.reload();
	await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
});
