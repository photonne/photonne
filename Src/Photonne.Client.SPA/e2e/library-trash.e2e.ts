import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { libraryState } from './fakes/library';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });

async function openTrash(page: Page, setup?: (state: ReturnType<typeof libraryState>) => void) {
	const api = await fakeApi(page, { signedIn: true });
	setup?.(libraryState(api.state));
	await page.goto('/trash');
	await expect(page.getByRole('heading', { name: 'Papelera', level: 1 })).toBeVisible();
	return api;
}

test('explains the retention and restores the selection with undo', async ({ page }) => {
	const api = await openTrash(page);

	await expect(page.getByRole('note')).toHaveText(
		'Lo que llega a la papelera se elimina definitivamente al cabo de 30 días.'
	);
	await expect(cells(page)).toHaveCount(10);
	const first = await cells(page).first().getAttribute('data-id');

	await cells(page)
		.first()
		.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Restaurar', exact: true }).click();

	await expect(page.getByText('1 restaurada')).toBeVisible();
	await expect(cells(page)).toHaveCount(9);
	expect(api.restored).toEqual([first]);

	await page.getByRole('button', { name: 'Deshacer' }).click();
	await expect.poll(() => api.removed).toEqual([first]);
});

test('deletes the selection for good with Delete, after confirming', async ({ page }) => {
	const api = await openTrash(page);
	await cells(page)
		.nth(1)
		.click({ modifiers: ['ControlOrMeta'] });
	await cells(page)
		.nth(2)
		.click({ modifiers: ['ControlOrMeta'] });

	await page.keyboard.press('Delete');

	const dialog = page.getByRole('dialog', { name: 'Eliminar definitivamente' });
	await expect(dialog).toContainText('Se eliminarán 2 elementos para siempre');
	// Cancel is the safe default.
	await expect(dialog.getByRole('button', { name: 'Cancelar' })).toBeFocused();
	await dialog.getByRole('button', { name: 'Eliminar', exact: true }).click();

	await expect(page.getByText('2 eliminadas definitivamente')).toBeVisible();
	await expect(cells(page)).toHaveCount(8);
	expect(libraryState(api.state).purged).toEqual(['2023-03-1', '2023-03-2']);
	// Nothing to undo after a purge.
	await expect(page.getByRole('button', { name: 'Deshacer' })).toHaveCount(0);
});

test('empties the trash and restores everything, each after confirming', async ({ page }) => {
	const api = await openTrash(page);

	await page.getByRole('button', { name: 'Restaurar todo' }).click();
	await page
		.getByRole('dialog', { name: 'Restaurar todo' })
		.getByRole('button', { name: 'Cancelar' })
		.click();
	await page.getByRole('button', { name: 'Vaciar papelera' }).click();
	const dialog = page.getByRole('dialog', { name: 'Vaciar papelera' });
	await expect(dialog).toContainText('No se puede deshacer');
	await dialog.getByRole('button', { name: 'Vaciar papelera' }).click();

	await expect(page.getByText('Papelera vaciada')).toBeVisible();
	await expect(page.getByText('La papelera está vacía.')).toBeVisible();
	expect(libraryState(api.state).calls).toEqual(['empty']);
	await expect(page.getByRole('button', { name: 'Restaurar todo' })).toBeDisabled();
});

test('restores everything', async ({ page }) => {
	const api = await openTrash(page);

	await page.getByRole('button', { name: 'Restaurar todo' }).click();
	await page
		.getByRole('dialog', { name: 'Restaurar todo' })
		.getByRole('button', { name: 'Restaurar todo' })
		.click();

	await expect(page.getByText('Papelera restaurada')).toBeVisible();
	await expect(page.getByText('La papelera está vacía.')).toBeVisible();
	expect(libraryState(api.state).calls).toEqual(['restore-all']);
});

test('says when the server keeps no trash or never purges it', async ({ page }) => {
	await openTrash(page, (state) => {
		state.settings['NightlyTaskSettings.TrashCleanup.Enabled'] = 'false';
	});
	await expect(page.getByRole('note')).toHaveText(
		'Lo que llega a la papelera se queda aquí hasta que lo restaures o la vacíes.'
	);

	await page.unrouteAll({ behavior: 'ignoreErrors' });
	await openTrash(page, (state) => {
		state.settings['TrashSettings.Enabled'] = 'false';
	});
	await expect(page.getByRole('note')).toContainText('La papelera está desactivada');
});

test('restores and deletes from the viewer', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto('/trash?asset=2023-03-0');
	const viewer = page.getByRole('dialog');

	await viewer.getByRole('button', { name: 'Restaurar' }).click();
	await expect(page).toHaveURL(/asset=2023-03-1/);
	expect(api.restored).toEqual(['2023-03-0']);

	await viewer.getByRole('button', { name: 'Eliminar definitivamente' }).click();
	const confirm = page.getByRole('dialog', { name: 'Eliminar definitivamente' });
	await expect(confirm).toContainText('Se eliminará 1 elemento');
	// Escape cancels the confirmation without closing the viewer behind it.
	await page.keyboard.press('Escape');
	await expect(confirm).toBeHidden();
	await expect(page).toHaveURL(/asset=2023-03-1/);

	await viewer.getByRole('button', { name: 'Eliminar definitivamente' }).click();
	await confirm.getByRole('button', { name: 'Eliminar', exact: true }).click();
	await expect(page).toHaveURL(/asset=2023-03-2/);
	await expect.poll(() => libraryState(api.state).purged).toEqual(['2023-03-1']);
});

test('switches to the shared folders trash when there is one', async ({ page }) => {
	const api = await openTrash(page, (state) => {
		state.sharedTrash = ['2026-09-0', '2026-09-1'].map((id) => ({
			id,
			fileName: `IMG_${id}.jpg`,
			fullPath: `/assets/shared/Familia/IMG_${id}.jpg`,
			fileSize: 2_000_000,
			type: 'Image',
			extension: '.jpg',
			hasThumbnails: true,
			width: 4000,
			height: 3000,
			deletedAt: '2026-10-05T09:00:00Z',
			deletedByUsername: 'luis',
			deletedFromPath: '/assets/shared/Familia',
			deletedFromFolderName: 'Familia'
		}));
	});

	await page.getByRole('link', { name: 'Carpetas compartidas' }).click();
	await expect(page.getByRole('link', { name: 'Carpetas compartidas' })).toHaveAttribute(
		'aria-current',
		'page'
	);
	await expect(cells(page)).toHaveCount(2);
	await expect(page.getByRole('button', { name: 'Vaciar papelera' })).toHaveCount(0);

	await cells(page)
		.first()
		.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Restaurar', exact: true }).click();

	await expect(page.getByText('1 restaurada')).toBeVisible();
	await expect(cells(page)).toHaveCount(1);
	expect(libraryState(api.state).calls).toEqual(['shared-restore']);
});
