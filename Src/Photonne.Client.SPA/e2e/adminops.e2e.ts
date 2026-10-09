import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { adminState } from './fakes/adminops';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 900 } });

const settingsNav = (page: Page) => page.getByRole('navigation', { name: 'Secciones de ajustes' });

async function open(page: Page, path: string) {
	const api = await fakeApi(page, { signedIn: true, role: 'Admin' });
	await page.goto(path);
	return adminState(api.state);
}

test.describe('tasks and queues', () => {
	test('lists background tasks and stops a running one', async ({ page }) => {
		const state = await open(page, '/admin/tasks');

		await expect(page.getByRole('heading', { name: 'Tareas y colas', level: 1 })).toBeVisible();
		const rows = page.getByRole('row');
		await expect(rows.filter({ hasText: 'Generar miniaturas' })).toContainText('En curso');
		await expect(rows.filter({ hasText: 'Miniaturas huérfanas' })).toContainText('Completada');
		await expect(rows.filter({ hasText: 'Indexar' })).toContainText(
			'El directorio interno no existe'
		);

		await rows
			.filter({ hasText: 'Generar miniaturas' })
			.getByRole('button', { name: 'Detener' })
			.click();
		await expect(page.getByText('Generar miniaturas: cancelada')).toBeVisible();
		expect(state.cancelled).toEqual(['00000000-0000-4000-8000-000000000001']);
		await expect(rows.filter({ hasText: 'Generar miniaturas' })).toContainText('Cancelada');
	});

	test('summarises the queues, links failures and empties a queue after confirming', async ({
		page
	}) => {
		const state = await open(page, '/admin/tasks');

		const faces = page.getByRole('row').filter({ hasText: 'Caras' });
		await expect(faces).toContainText('120');
		await faces.getByRole('button', { name: 'Vaciar cola' }).click();
		const dialog = page.getByRole('dialog', { name: '¿Vaciar la cola de Caras?' });
		await dialog.getByRole('button', { name: 'Vaciar cola' }).click();
		await expect(page.getByText(/Cola vaciada: 120 trabajos eliminados/)).toBeVisible();
		expect(state.emptied).toEqual(['face-recognition']);

		await page
			.getByRole('row')
			.filter({ hasText: 'Metadatos' })
			.getByRole('link', { name: '2' })
			.click();
		await expect(page).toHaveURL(/\/admin\/tasks\/failures\?type=Exif$/);
		await expect(page.getByRole('button', { name: /^Metadatos/ })).toHaveAttribute(
			'aria-pressed',
			'true'
		);
	});
});

test.describe('assets with problems', () => {
	test('filters, retries, dismisses a selection and retries everything', async ({ page }) => {
		const state = await open(page, '/admin/tasks/failures');

		await expect(page.getByText('3 tareas con problemas')).toBeVisible();
		await page.getByRole('button', { name: /^Pasajero/ }).click();
		await expect(page).toHaveURL(/kind=Transient/);
		await expect(page.getByRole('row')).toHaveCount(2);
		await page.getByRole('button', { name: 'Reintentar IMG_202603.jpg' }).click();
		await expect(page.getByText('1 tarea enviada a la cola.')).toBeVisible();
		expect(state.retried).toEqual(['00000000-0000-4000-9000-000000000003']);

		await page.getByRole('button', { name: 'Todas' }).nth(1).click();
		await page.getByRole('checkbox', { name: 'Seleccionar IMG_202602.jpg' }).check();
		await expect(page.getByText('1 seleccionada')).toBeVisible();
		await page.getByRole('button', { name: 'Descartar', exact: true }).click();
		await expect(page.getByText(/1 tarea descartada/)).toBeVisible();
		expect(state.suppressed).toEqual(['00000000-0000-4000-9000-000000000002']);

		await page.getByRole('button', { name: /^Reintentar \d+ tareas?$/ }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Reintentar' }).click();
		await expect(page.getByText('1 tarea enviada a la cola.').last()).toBeVisible();
		expect(state.retried).toContain('00000000-0000-4000-9000-000000000001');
	});
});

test.describe('maintenance', () => {
	test('starts jobs with their options and asks before deleting', async ({ page }) => {
		const state = await open(page, '/admin/maintenance');

		const metadata = page
			.getByRole('listitem')
			.filter({ has: page.getByRole('heading', { name: 'Extraer metadatos' }) });
		await metadata.getByRole('checkbox', { name: /Volver a leer todos/ }).check();
		await metadata.getByRole('button', { name: 'Ejecutar: Extraer metadatos' }).click();
		await expect.poll(() => state.started).toContain('Metadata?overwrite=true');
		await expect(
			metadata.getByRole('button', { name: 'Detener: Extraer metadatos' })
		).toBeVisible();

		// Destructive: nothing starts until confirmed.
		await page.getByRole('button', { name: 'Ejecutar: Vaciar papeleras' }).click();
		const dialog = page.getByRole('dialog', { name: '¿Vaciar todas las papeleras?' });
		await expect(dialog).toBeVisible();
		await dialog.getByRole('button', { name: 'Cancelar' }).click();
		expect(state.started.some((s) => s.startsWith('empty-trash'))).toBe(false);
		await page.getByRole('button', { name: 'Ejecutar: Vaciar papeleras' }).click();
		await dialog.getByRole('button', { name: 'Ejecutar' }).click();
		await expect.poll(() => state.started).toContain('empty-trash');

		// A simulated purge deletes nothing, so it doesn't ask.
		const purge = page
			.getByRole('listitem')
			.filter({ has: page.getByRole('heading', { name: 'Purgar assets sin archivo' }) });
		await purge.getByRole('checkbox', { name: /Solo simular/ }).check();
		await purge.getByRole('button', { name: 'Ejecutar: Purgar assets sin archivo' }).click();
		await expect.poll(() => state.started).toContain('purge-missing?dryRun=true');
	});

	test('queues AI work, reports a refusal and re-clusters faces', async ({ page }) => {
		const state = await open(page, '/admin/maintenance');

		const faces = page
			.getByRole('listitem')
			.filter({ has: page.getByRole('heading', { name: 'Reconocimiento facial' }) });
		await expect(faces).toContainText('340 sin procesar');
		await faces.getByRole('button', { name: 'Encolar pendientes: Reconocimiento facial' }).click();
		await expect(page.getByText(/Reconocimiento facial: encoladas 340 de 340/)).toBeVisible();
		expect(state.backfills).toEqual(['face-recognition']);

		await page
			.getByRole('button', { name: 'Encolar pendientes: Reconocimiento de texto (OCR)' })
			.click();
		await expect(
			page.getByText('El reconocimiento de texto está desactivado en Ajustes.')
		).toBeVisible();

		await faces.getByRole('button', { name: 'Reagrupar caras' }).click();
		await expect.poll(() => state.started).toContain('FaceClustering');
		await expect(faces.getByText('Reagrupando caras')).toBeVisible();
	});

	test('shows the last coverage check and folds a group, remembering it', async ({ page }) => {
		await open(page, '/admin/maintenance');

		await expect(page.getByText('5702 archivos · 5690 indexados · 10 sin soporte')).toBeVisible();
		await page.getByText('Archivos sin indexar').click();
		await expect(page.getByText('/assets/users/ana/Camera/IMG_9999.heic')).toBeVisible();

		const fold = page.getByRole('button', { name: 'Limpiar', exact: true });
		await expect(fold).toHaveAttribute('aria-expanded', 'true');
		await fold.click();
		await expect(fold).toHaveAttribute('aria-expanded', 'false');
		await expect(page.getByRole('heading', { name: 'Vaciar papeleras' })).toHaveCount(0);
		await page.reload();
		await expect(page.getByRole('button', { name: 'Limpiar', exact: true })).toHaveAttribute(
			'aria-expanded',
			'false'
		);
	});

	test('scans the disk for duplicates and deletes the chosen copies', async ({ page }) => {
		const state = await open(page, '/admin/maintenance/duplicates');

		await page.getByLabel(/Escanear el disco/).check();
		await page.getByRole('button', { name: 'Empezar' }).click();
		await expect(page.getByText('2 archivos idénticos')).toBeVisible();
		await expect(page.getByText('3 archivos idénticos')).toBeVisible();

		// Auto-selection keeps one copy per group: 1 + 2 to delete.
		const remove = page.getByRole('button', { name: /^Borrar 3 archivos/ });
		await expect(remove).toBeEnabled();
		// Keep the camera copy too.
		await page.getByRole('button', { name: /IMG_0002\.jpg.*Camera/ }).click();
		await expect(page.getByRole('button', { name: /^Borrar 2 archivos/ })).toBeVisible();

		await page.getByRole('button', { name: /^Borrar 2 archivos/ }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Borrar' }).click();
		await expect(page.getByText('2 archivos borrados.')).toBeVisible();
		expect(state.deletedFiles.sort()).toEqual(
			['/data/Descargas/IMG_0001 (1).jpg', '/data/Escritorio/copia.jpg'].sort()
		);
		await expect(page.getByText('2 archivos idénticos')).toBeVisible();
		await expect(page.getByText('3 archivos idénticos')).toHaveCount(0);
	});
});

test.describe('server settings', () => {
	test('opens on the first section, validates and saves only what changed', async ({ page }) => {
		const state = await open(page, '/admin/settings');

		await expect(page).toHaveURL(/\/admin\/settings\/server$/);
		const url = page.getByLabel('URL pública');
		await expect(url).toHaveValue('https://fotos.example.com');
		await url.fill('fotos sin protocolo');
		await expect(page.getByText('Tiene que ser una dirección http:// o https://.')).toBeVisible();
		await expect(page.getByRole('button', { name: 'Guardar' })).toBeDisabled();

		await url.fill('https://photos.example.org');
		const session = page.getByLabel('Duración de la sesión');
		await session.fill('2');
		await expect(page.getByText('Entre 5 y 43200.')).toBeVisible();
		await session.fill('60');
		await expect(page.getByText('2 cambios sin guardar')).toBeVisible();
		await page.keyboard.press('Control+s');
		await expect(page.getByText('Ajustes guardados.')).toBeVisible();
		// Each setting is its own request, all at once: they land in any order.
		expect([...state.saved].sort()).toEqual([
			['ServerSettings.PublicUrl', 'https://photos.example.org'],
			['ServerSettings.SessionTimeoutMinutes', '60']
		]);
		await expect(page.getByText('Sin cambios')).toBeVisible();
	});

	test('checks the face thresholds against each other and guards unsaved edits', async ({
		page
	}) => {
		const state = await open(page, '/admin/settings/faces');

		const suggestion = page.getByRole('textbox', { name: 'Umbral de sugerencias' });
		await suggestion.fill('0,3');
		await expect(
			page.getByText('Tiene que ser igual o mayor que el umbral de agrupado.')
		).toBeVisible();
		await suggestion.fill('0,6');
		await page.getByRole('switch', { name: 'Procesar cada noche' }).check();
		await expect(page.getByLabel('Qué procesar')).toBeVisible();

		await settingsNav(page).getByRole('link', { name: 'Papelera' }).click();
		const dialog = page.getByRole('dialog', { name: '¿Descartar los cambios?' });
		await dialog.getByRole('button', { name: 'Seguir editando' }).click();
		await expect(page).toHaveURL(/\/faces$/);
		await page.getByRole('button', { name: 'Guardar' }).click();
		await expect(page.getByText('Ajustes guardados.')).toBeVisible();
		// Each setting is its own request, all at once: they land in any order.
		expect([...state.saved].sort()).toEqual([
			['FaceRecognition.SuggestionThreshold', '0.6'],
			['NightlyTaskSettings.FaceRecognition.Enabled', 'true']
		]);

		await settingsNav(page).getByRole('link', { name: 'Papelera' }).click();
		await expect(page.getByRole('heading', { name: 'Papelera', level: 1 })).toBeVisible();
	});

	test('cleans up the expired trash and forces the next nightly run', async ({ page }) => {
		const state = await open(page, '/admin/settings/trash');

		await expect(page.getByRole('row').filter({ hasText: 'ana' })).toContainText('Excede la cuota');
		await page.getByRole('button', { name: 'Borrar 6 caducados' }).click();
		await page.getByRole('dialog').getByRole('button', { name: 'Borrar' }).click();
		await expect(page.getByText('6 elementos caducados borrados.')).toBeVisible();
		expect(state.trashCleaned).toBe(1);

		await settingsNav(page).getByRole('link', { name: 'Tareas nocturnas' }).click();
		await expect(page.getByText('Último ciclo: 2026-10-07')).toBeVisible();
		await page.getByRole('button', { name: 'Forzar la siguiente ejecución' }).click();
		await expect(page.getByText(/El ciclo volverá a ejecutarse/)).toBeVisible();
		expect(state.saved).toContainEqual(['NightlyTaskSettings.LastRunDate', '']);
	});

	test('is read-only in the demo', async ({ page }) => {
		const api = await fakeApi(page, { signedIn: true, role: 'Admin' });
		adminState(api.state).demo = true;
		await page.goto('/admin/settings/server');

		await expect(page.getByText(/Solo lectura en la demo/)).toBeVisible();
		await expect(page.getByLabel('URL pública')).toBeDisabled();
	});
});
