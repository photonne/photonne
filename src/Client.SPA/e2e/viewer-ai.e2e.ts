import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { viewerState } from './fakes/viewer';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('shows each analysis, re-runs one and follows it until it is done', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/?asset=2026-09-0');
	await expect(page.getByRole('dialog', { name: 'IMG_202609_0.jpg' })).toBeVisible();
	await page.keyboard.press('i');
	const panel = page.getByRole('complementary', { name: 'Información' });
	await panel.getByRole('tab', { name: 'IA' }).click();

	const rows = panel.getByRole('list', { name: 'Análisis con IA' }).getByRole('listitem');
	await expect(rows).toHaveCount(5);
	await expect(rows.nth(0)).toContainText('Caras');
	await expect(rows.nth(0)).toContainText(/Hecho hace/);
	await expect(rows.nth(1)).toContainText('Error: ML service unavailable');
	await expect(rows.nth(4)).toContainText('Sin analizar');

	await rows.nth(1).getByRole('button', { name: 'Volver a analizar: Objetos' }).click();
	await expect.poll(() => viewerState(api.state).retried).toEqual(['ObjectDetection']);
	// Polled every few seconds while it runs: queued or running, then done.
	await expect(rows.nth(1).getByRole('progressbar')).toBeVisible();
	await expect(rows.nth(1)).toContainText(/Hecho/, { timeout: 15_000 });
	await expect(rows.nth(1).getByRole('progressbar')).toBeHidden();
});

test('runs every analysis at once, and retries only the failed ones', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/?asset=2026-09-0');
	await expect(page.getByRole('dialog', { name: 'IMG_202609_0.jpg' })).toBeVisible();
	await page.keyboard.press('i');
	const panel = page.getByRole('complementary', { name: 'Información' });
	await panel.getByRole('tab', { name: 'IA' }).click();

	await panel.getByRole('button', { name: 'Reintentar los que han fallado' }).click();
	await expect(page.getByText('Se reintenta 1 análisis')).toBeVisible();
	expect(viewerState(api.state).retried).toEqual(['ObjectDetection']);

	await expect(panel.getByRole('button', { name: 'Analizar todo' })).toBeEnabled({
		timeout: 15_000
	});
	await panel.getByRole('button', { name: 'Analizar todo' }).click();
	await expect
		.poll(() => viewerState(api.state).retried.slice(1))
		.toEqual([
			'FaceRecognition',
			'ObjectDetection',
			'SceneClassification',
			'TextRecognition',
			'ImageEmbedding'
		]);
});
