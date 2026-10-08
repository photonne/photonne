import { test } from '@playwright/test';
import { fakeApi } from './fake-api';

// Not an assertion: renders the timeline to a file for visual review.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES', viewport: { width: 1440, height: 900 } });

test('timeline', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto('/');
	await page.waitForTimeout(800);
	await page.screenshot({ path: 'test-results/timeline.png' });
	await page.getByRole('button', { name: /Foto/ }).nth(3).hover();
	await page.keyboard.down('Control');
	await page.getByRole('button', { name: /Foto/ }).nth(3).click();
	await page.getByRole('button', { name: /Foto/ }).nth(5).click();
	await page.keyboard.up('Control');
	await page.mouse.wheel(0, 2500);
	await page.waitForTimeout(800);
	await page.screenshot({ path: 'test-results/timeline-scrolled.png' });
});
