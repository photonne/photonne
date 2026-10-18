import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { summariesState } from './fakes/summaries';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('asks for the summary of a year from Recuerdos and follows it until it is ready', async ({
	page
}) => {
	const api = await fakeApi(page, { signedIn: true });
	const data = summariesState(api.state);
	await page.goto('/memories');

	await page.getByRole('button', { name: 'Vídeo resumen del año' }).click();
	const dialog = page.getByRole('dialog', { name: 'Vídeo resumen del año' });
	await expect(dialog.getByRole('combobox', { name: 'Año' })).toHaveValue('2026');
	await expect(dialog.getByRole('combobox', { name: 'Año' }).getByRole('option')).toHaveText([
		'2026 · 42 vídeos',
		'2025 · 1 vídeo'
	]);

	// Photos are the user's choice: off until they turn them on.
	const photos = dialog.getByRole('checkbox', { name: /Incluir fotos/ });
	await expect(photos).not.toBeChecked();
	await photos.check();

	// Five minutes and every shape unless the user says otherwise.
	const duration = dialog.getByRole('combobox', { name: 'Duración máxima' });
	await expect(duration).toHaveValue('5');
	await expect(duration.getByRole('option')).toHaveCount(10);
	await duration.selectOption({ label: '3 minutos' });
	const orientation = dialog.getByRole('radiogroup', { name: 'Orientación' });
	await expect(orientation.getByRole('radio', { name: 'Todos' })).toBeChecked();
	await orientation.getByRole('radio', { name: 'Verticales' }).check();
	await expect(dialog.getByText('en un vídeo vertical para el móvil')).toBeVisible();

	await dialog.getByRole('button', { name: 'Crear vídeo' }).click();

	await expect(
		page.getByText('Creando «Resumen 2026». Te avisaremos cuando esté listo.')
	).toBeVisible();
	expect(data.requests).toEqual([
		{
			source: 'year',
			year: 2026,
			albumId: null,
			includePhotos: true,
			orientation: 'portrait',
			durationMinutes: 3
		}
	]);

	await expect(dialog.getByText('Montando el vídeo… 40 %')).toBeVisible();
	await expect(dialog.getByRole('button', { name: 'Crear vídeo' })).toBeDisabled();

	await expect(dialog.getByText('Listo: «Resumen 2026» ya está en tu biblioteca.')).toBeVisible({
		timeout: 10_000
	});
	await expect(dialog.getByRole('link', { name: 'Ver vídeo' })).toHaveAttribute(
		'href',
		'/videos?asset=2026-09-0'
	);
	await expect(dialog.getByRole('button', { name: 'Volver a crear' })).toBeEnabled();
});

test('asks for an album summary from the album menu and explains when there is too little', async ({
	page
}) => {
	const api = await fakeApi(page, { signedIn: true });
	const data = summariesState(api.state);
	data.notEnough = true;
	await page.goto('/albums/album-1');

	await page.getByRole('button', { name: 'Más acciones' }).click();
	await page.getByRole('menuitem', { name: 'Crear vídeo resumen' }).click();
	const dialog = page.getByRole('dialog', { name: 'Vídeo resumen del álbum' });
	// No year to pick for an album.
	await expect(dialog.getByRole('combobox', { name: 'Año' })).toHaveCount(0);

	await dialog.getByRole('button', { name: 'Crear vídeo' }).click();

	await expect(dialog.getByRole('alert')).toHaveText(
		'No hay suficientes vídeos. Prueba a incluir las fotos.'
	);
	expect(data.requests).toEqual([
		{
			source: 'album',
			year: null,
			albumId: 'album-1',
			includePhotos: false,
			orientation: 'all',
			durationMinutes: 5
		}
	]);
});

test('starts from the options of the last summary of that year', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	const data = summariesState(api.state);
	data.list = [
		{
			id: 'done',
			source: 'year',
			year: 2026,
			albumId: null,
			title: 'Resumen 2026',
			includePhotos: true,
			orientation: 'landscape',
			durationMinutes: 8,
			status: 'completed',
			progress: 1,
			error: null,
			assetId: '2026-09-0',
			clipCount: 40,
			requestedAt: '2026-10-18T10:00:00Z',
			completedAt: '2026-10-18T10:05:00Z'
		}
	];
	await page.goto('/memories');
	await page.getByRole('button', { name: 'Vídeo resumen del año' }).click();
	const dialog = page.getByRole('dialog', { name: 'Vídeo resumen del año' });

	await expect(dialog.getByRole('combobox', { name: 'Duración máxima' })).toHaveValue('8');
	await expect(dialog.getByRole('radio', { name: 'Horizontales' })).toBeChecked();
	await expect(dialog.getByRole('checkbox', { name: /Incluir fotos/ })).toBeChecked();
	await expect(dialog.getByRole('button', { name: 'Volver a crear' })).toBeEnabled();
});
