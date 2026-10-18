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
	await expect(dialog.getByRole('option')).toHaveText(['2026 · 42 vídeos', '2025 · 1 vídeo']);

	// Photos are the user's choice: off until they turn them on.
	const photos = dialog.getByRole('checkbox', { name: /Incluir fotos/ });
	await expect(photos).not.toBeChecked();
	await photos.check();
	await dialog.getByRole('button', { name: 'Crear vídeo' }).click();

	await expect(
		page.getByText('Creando «Resumen 2026». Te avisaremos cuando esté listo.')
	).toBeVisible();
	expect(data.requests).toEqual([
		{ source: 'year', year: 2026, albumId: null, includePhotos: true }
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
	await expect(dialog.getByRole('combobox')).toHaveCount(0);

	await dialog.getByRole('button', { name: 'Crear vídeo' }).click();

	await expect(dialog.getByRole('alert')).toHaveText(
		'No hay suficientes vídeos. Prueba a incluir las fotos.'
	);
	expect(data.requests).toEqual([
		{ source: 'album', year: null, albumId: 'album-1', includePhotos: false }
	]);
});
