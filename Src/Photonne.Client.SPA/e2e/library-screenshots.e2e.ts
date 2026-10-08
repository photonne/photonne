import { test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { libraryState } from './fakes/library';

// Not assertions: renders the archive, trash, memories and explore pages to
// files for visual review, wide and narrow, light and dark.
test.skip(!process.env.SCREENSHOTS, 'only with SCREENSHOTS=1');
test.use({ locale: 'es-ES' });

const pages: [string, string][] = [
	['archive', '/archive'],
	['trash', '/trash'],
	['memories', '/memories'],
	['memory', '/memories/mem-today-1'],
	['explore', '/explore'],
	['scenes', '/explore/scenes'],
	['scene', '/explore/scenes/beach'],
	['text', '/explore/text'],
	['theme', '/explore/themes/scene%3Abeach']
];

async function shoot(page: Page, name: string) {
	await page.waitForTimeout(700);
	await page.screenshot({ path: `test-results/library-${name}.png` });
}

for (const [width, height] of [
	[1280, 800],
	[900, 700]
]) {
	for (const scheme of ['light', 'dark'] as const) {
		test(`pages at ${width} ${scheme}`, async ({ page }) => {
			await page.setViewportSize({ width, height });
			await page.emulateMedia({ colorScheme: scheme });
			const api = await fakeApi(page, { signedIn: true });
			libraryState(api.state).sharedTrash = [
				{
					id: '2026-09-0',
					fileName: 'IMG_1.jpg',
					fullPath: '/assets/shared/Familia/IMG_1.jpg',
					fileSize: 1,
					type: 'Image',
					extension: '.jpg',
					hasThumbnails: true,
					width: 4000,
					height: 3000,
					deletedAt: '2026-10-05T09:00:00Z',
					deletedByUsername: 'luis',
					deletedFromPath: '/assets/shared/Familia',
					deletedFromFolderName: 'Familia'
				}
			];
			for (const [name, path] of pages) {
				await page.goto(path);
				await shoot(page, `${name}-${width}-${scheme}`);
			}
			if (scheme === 'light') {
				await page.goto('/trash');
				await page
					.getByRole('button', { name: /^Foto/ })
					.first()
					.click({ modifiers: ['ControlOrMeta'] });
				await shoot(page, `trash-selected-${width}`);
				await page.keyboard.press('Delete');
				await shoot(page, `trash-confirm-${width}`);
				await page.goto('/trash?asset=2023-03-0');
				await shoot(page, `trash-viewer-${width}`);
				await page.goto('/memories/mem-today-1');
				await page.getByRole('button', { name: 'Reproducir' }).click();
				await page.waitForTimeout(1200);
				await shoot(page, `story-${width}`);
			}
		});
	}
}
