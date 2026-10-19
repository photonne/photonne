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
			durationMinutes: 3,
			showTitles: true,
			musicId: null,
			musicName: null,
			review: false
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
			durationMinutes: 5,
			showTitles: true,
			musicId: null,
			musicName: null,
			review: false
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
			showTitles: false,
			musicId: 'music-7',
			musicName: 'Fiesta',
			review: false,
			hasPlan: true,
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
	await expect(dialog.getByRole('checkbox', { name: /Título y rótulos/ })).not.toBeChecked();
	await expect(dialog.getByText('Fiesta')).toBeVisible();
	await expect(dialog.getByRole('button', { name: 'Volver a crear' })).toBeEnabled();
	// The clips it was cut from can still be changed and rendered again.
	await expect(dialog.getByRole('link', { name: 'Cambiar fragmentos' })).toHaveAttribute(
		'href',
		'/summaries/done'
	);
});

test('lays music under the summary and can do without titles', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	const data = summariesState(api.state);
	await page.goto('/memories');
	await page.getByRole('button', { name: 'Vídeo resumen del año' }).click();
	const dialog = page.getByRole('dialog', { name: 'Vídeo resumen del año' });
	await expect(dialog.getByRole('combobox', { name: 'Año' })).toHaveValue('2026');

	const titles = dialog.getByRole('checkbox', { name: /Título y rótulos/ });
	await expect(titles).toBeChecked();
	await titles.uncheck();

	await dialog.getByLabel('Música (opcional)').setInputFiles({
		name: 'verano.mp3',
		mimeType: 'audio/mpeg',
		buffer: Buffer.from('not really music, the fake server takes it')
	});
	await expect(dialog.getByText('Verano', { exact: true })).toBeVisible();

	// Changed their mind, and then back.
	await dialog.getByRole('button', { name: 'Quitar' }).click();
	await expect(dialog.getByText('Verano', { exact: true })).toHaveCount(0);
	await dialog.getByLabel('Música (opcional)').setInputFiles({
		name: 'verano.mp3',
		mimeType: 'audio/mpeg',
		buffer: Buffer.from('again')
	});
	await expect(dialog.getByText('Verano', { exact: true })).toBeVisible();

	await dialog.getByRole('button', { name: 'Crear vídeo' }).click();
	await expect(dialog.getByText('Montando el vídeo… 40 %')).toBeVisible();
	expect(data.requests).toEqual([
		{
			source: 'year',
			year: 2026,
			albumId: null,
			includePhotos: false,
			orientation: 'all',
			durationMinutes: 5,
			showTitles: false,
			musicId: 'music-1',
			musicName: 'Verano',
			review: false
		}
	]);
});

test('reviews the clips before rendering: trims, swaps, pins and removes', async ({ page }) => {
	// Walks the request, the plan and the render through several polls.
	test.slow();
	const api = await fakeApi(page, { signedIn: true });
	const data = summariesState(api.state);
	await page.goto('/memories');
	await page.getByRole('button', { name: 'Vídeo resumen del año' }).click();
	const dialog = page.getByRole('dialog', { name: 'Vídeo resumen del año' });
	await expect(dialog.getByRole('combobox', { name: 'Año' })).toHaveValue('2026');

	await dialog.getByRole('checkbox', { name: /Revisar los fragmentos/ }).check();
	await dialog.getByRole('button', { name: 'Crear vídeo' }).click();
	expect(data.requests[0].review).toBe(true);

	// Chosen, not rendered: the dialog sends the user to review.
	await expect(
		dialog.getByText('Los fragmentos de «Resumen 2026» están elegidos.', { exact: false })
	).toBeVisible({
		timeout: 10_000
	});
	await dialog.getByRole('link', { name: 'Revisar fragmentos' }).click();
	await expect(page).toHaveURL(/\/summaries\/summary-1$/);

	await expect(page.getByRole('heading', { name: 'Resumen 2026' })).toBeVisible();
	await expect(page.getByText('0:13 de 5:00 como máximo · 3 fragmentos')).toBeVisible();
	const clips = page.getByRole('list', { name: 'Fragmentos del vídeo' }).getByRole('listitem');
	await expect(clips).toHaveCount(3);

	// The first moment starts later.
	await clips.nth(0).getByRole('spinbutton', { name: 'Empieza (s)' }).fill('4');
	await clips.nth(0).getByRole('spinbutton', { name: 'Empieza (s)' }).blur();
	await expect(clips.nth(0).getByText('0:04 de 0:20')).toBeVisible();

	// The second is swapped for another video from around then.
	await clips.nth(1).getByRole('button', { name: 'Cambiar' }).click();
	await clips.nth(1).getByRole('button', { name: 'Usar Vídeo del 2 mar 2026' }).click();

	// The photo always goes in; then the user changes their mind about the first.
	await clips.nth(2).getByRole('button', { name: 'Incluir siempre' }).click();
	await expect(clips.nth(2).getByRole('button', { name: 'Incluir siempre' })).toHaveAttribute(
		'aria-pressed',
		'true'
	);
	await clips.nth(0).getByRole('button', { name: 'Quitar' }).click();
	await expect(clips).toHaveCount(2);

	await page.getByRole('button', { name: 'Montar vídeo' }).click();
	await expect(
		page.getByText('Creando «Resumen 2026». Te avisaremos cuando esté listo.')
	).toBeVisible();
	expect(data.edits).toEqual([
		{
			clips: [
				{ assetId: '2026-03-5', start: 10.4, length: 4, pinned: false },
				{ assetId: '2026-03-2', start: 0, length: 3, pinned: true }
			]
		}
	]);
	expect(data.renders).toEqual(['summary-1']);

	await expect(page.getByText('Listo: «Resumen 2026» ya está en tu biblioteca.')).toBeVisible({
		timeout: 20_000
	});
	await expect(page.getByRole('link', { name: 'Ver vídeo' })).toHaveAttribute(
		'href',
		'/videos?asset=2026-09-0'
	);
});

test('will not save a cut longer than the summary may be', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	const data = summariesState(api.state);
	data.list = [
		{
			id: 'short',
			source: 'year',
			year: 2026,
			albumId: null,
			title: 'Resumen 2026',
			includePhotos: true,
			orientation: 'all',
			durationMinutes: 1,
			showTitles: true,
			musicId: null,
			musicName: null,
			review: true,
			hasPlan: true,
			status: 'planned',
			progress: 1,
			error: null,
			assetId: null,
			clipCount: 2,
			requestedAt: '2026-10-18T10:00:00Z',
			completedAt: '2026-10-18T10:01:00Z'
		}
	];
	data.plans.short = {
		summaryId: 'short',
		title: 'Resumen 2026',
		status: 'planned',
		targetSeconds: 60,
		totalSeconds: 13,
		alternatives: 0,
		clips: [
			{
				assetId: 'v',
				isVideo: true,
				capturedAt: '2026-03-01T10:00:00',
				start: 0,
				length: 10,
				sourceSeconds: 120,
				pinned: false
			},
			{
				assetId: 'p',
				isVideo: false,
				capturedAt: '2026-03-02T10:00:00',
				start: 0,
				length: 3,
				sourceSeconds: 0,
				pinned: false
			}
		]
	};
	await page.goto('/summaries/short');

	const clip = page
		.getByRole('list', { name: 'Fragmentos del vídeo' })
		.getByRole('listitem')
		.first();
	await clip.getByRole('spinbutton', { name: 'Acaba (s)' }).fill('100');
	await clip.getByRole('spinbutton', { name: 'Acaba (s)' }).blur();

	await expect(page.getByRole('alert')).toHaveText(
		'Los fragmentos duran más que el máximo elegido. Quita alguno o acórtalos.'
	);
	await expect(page.getByRole('button', { name: 'Montar vídeo' })).toBeDisabled();
	await expect(page.getByRole('button', { name: 'Guardar' })).toBeDisabled();
	expect(data.edits).toEqual([]);
});
