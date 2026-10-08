import { test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';

// Not assertions: renders the account pages to files for visual review, wide
// and narrow, light and dark.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES' });

const pages: [string, string, boolean][] = [
	['upload', '/upload', true],
	['notifications', '/notifications', true],
	['profile', '/settings/profile', true],
	['security', '/settings/security', true],
	['appearance', '/settings/appearance', true],
	['storage', '/settings/storage', true],
	['shared-folders', '/settings/shared-folders', true],
	['share', '/share/boda', false],
	['share-password', '/share/privado', false],
	['share-expired', '/share/caducado', false]
];

async function shoot(page: Page, name: string) {
	await page.waitForTimeout(600);
	await page.screenshot({ path: `test-results/account-${name}.png` });
}

for (const [width, height, suffix] of [
	[1280, 800, ''],
	[900, 760, '-narrow']
] as const) {
	for (const scheme of ['light', 'dark'] as const) {
		test(`account pages ${width} ${scheme}`, async ({ page }) => {
			await page.setViewportSize({ width, height });
			await page.emulateMedia({ colorScheme: scheme });
			for (const [name, path, signedIn] of pages) {
				await page.unrouteAll();
				await fakeApi(page, { signedIn });
				await page.goto(path);
				if (name === 'upload') {
					await page.locator('input[type="file"][multiple]').setInputFiles([
						{ name: 'IMG_0001.jpg', mimeType: 'image/jpeg', buffer: Buffer.alloc(2_000_000) },
						{ name: 'IMG_0002.HEIC', mimeType: 'image/heic', buffer: Buffer.alloc(900_000) },
						{ name: 'VID_0003.mov', mimeType: 'video/quicktime', buffer: Buffer.alloc(3_000_000) }
					]);
				}
				await shoot(page, `${name}${suffix}-${scheme}`);
			}
			await page.goto('/share/boda');
			await page.waitForTimeout(400);
			await page.mouse.wheel(0, 700);
			await shoot(page, `share-scrolled${suffix}-${scheme}`);
			await page.goto('/share/boda?asset=00000000-0000-0000-0000-000000000101');
			await shoot(page, `share-viewer${suffix}-${scheme}`);
		});
	}
}
