import { expect, test, type Page } from '@playwright/test';
import { fakeApi } from './fake-api';
import { albumsApi } from './fakes/albums';

// Tall enough for a folder's twenty photos, laid out as a masonry, to all be
// rendered: the grid only renders what's within two screens of the viewport.
test.use({ locale: 'es-ES', viewport: { width: 1280, height: 1400 } });

const cells = (page: Page) => page.getByRole('button', { name: /^(Foto|Vídeo), / });
const tree = (page: Page) => page.getByRole('tree', { name: 'Árbol de carpetas' });
const item = (page: Page, name: string) =>
	tree(page).getByRole('treeitem', { name: new RegExp(`^${name},`) });

async function open(page: Page, path: string) {
	const api = await fakeApi(page, { signedIn: true });
	const folders = await albumsApi(page, api.state);
	await page.goto(path);
	return folders;
}

const calls = (state: { log: { call: string }[] }, call: string) =>
	state.log.filter((entry) => entry.call === call);

test('lists the top-level folders and opens one from the tree', async ({ page }) => {
	await open(page, '/folders');

	await expect(page.getByRole('heading', { name: 'Carpetas', level: 1 })).toBeVisible();
	await expect(
		page.getByRole('list', { name: 'Carpetas principales' }).getByRole('link')
	).toHaveCount(3);
	// Grouped, without the trash (it has its own page).
	await expect(tree(page).getByRole('treeitem', { level: 1 })).toHaveText([
		'Personal',
		'Compartido'
	]);
	await expect(item(page, '_trash')).toHaveCount(0);
	await expect(item(page, 'Camera')).toHaveAttribute('aria-expanded', 'false');

	await item(page, 'Camera').click();

	await expect(page).toHaveURL(/\/folders\/folder-1$/);
	await expect(page.getByRole('heading', { name: 'Camera', level: 1 })).toBeVisible();
	await expect(item(page, 'Camera')).toHaveAttribute('aria-selected', 'true');
	await expect(cells(page)).toHaveCount(20);
	await expect(
		page.getByRole('list', { name: 'Subcarpetas' }).getByRole('link', { name: /2025/ })
	).toBeVisible();
});

test('walks the tree with the keyboard', async ({ page }) => {
	await open(page, '/folders');

	await item(page, 'Camera').focus();
	await page.keyboard.press('ArrowRight');
	await expect(item(page, 'Camera')).toHaveAttribute('aria-expanded', 'true');
	await page.keyboard.press('ArrowRight');
	await expect(item(page, '2025')).toBeFocused();
	await page.keyboard.press('ArrowDown');
	await expect(item(page, 'Documentos')).toBeFocused();
	await page.keyboard.press('Home');
	await expect(tree(page).getByRole('treeitem', { name: 'Personal', exact: true })).toBeFocused();
	await page.keyboard.press('ArrowDown');
	await expect(item(page, 'Camera')).toBeFocused();
	await page.keyboard.press('ArrowLeft');
	await expect(item(page, 'Camera')).toHaveAttribute('aria-expanded', 'false');
	await page.keyboard.press('End');
	await page.keyboard.press('Enter');

	await expect(page).toHaveURL(/\/folders\/folder-4$/);
	await expect(page.getByRole('heading', { name: 'Familia', level: 1 })).toBeVisible();
});

test('a subfolder shows its path and the tree opens down to it', async ({ page }) => {
	await open(page, '/folders/folder-2');

	await expect(page.getByRole('heading', { name: '2025', level: 1 })).toBeVisible();
	const crumbs = page.getByRole('navigation', { name: 'Ruta de la carpeta' });
	await expect(crumbs.getByRole('link')).toHaveText(['Carpetas', 'Camera']);
	await expect(item(page, 'Camera')).toHaveAttribute('aria-expanded', 'true');
	await expect(item(page, '2025')).toHaveAttribute('aria-selected', 'true');
});

test('dragging photos onto a folder in the tree moves them', async ({ page }) => {
	const state = await open(page, '/folders/folder-1');
	await expect(cells(page)).toHaveCount(20);
	const first = await cells(page).first().getAttribute('data-id');

	await cells(page).first().dragTo(item(page, 'Documentos'));

	await expect(page.getByText('1 movida a «Documentos»')).toBeVisible();
	await expect(cells(page)).toHaveCount(19);
	expect(calls(state, 'POST /api/folders/assets/move')[0].body).toEqual({
		sourceFolderId: 'folder-1',
		targetFolderId: 'folder-3',
		assetIds: [first]
	});
	await expect(item(page, 'Documentos')).toHaveAccessibleName('Documentos, 1 elemento');

	await page.getByRole('button', { name: 'Deshacer' }).click();
	await expect(cells(page)).toHaveCount(20);
});

test('dragging a folder onto another moves it there', async ({ page }) => {
	const state = await open(page, '/folders');

	await item(page, 'Documentos').dragTo(item(page, 'Familia'));

	await expect(page.getByText('«Documentos» movida a «Familia»')).toBeVisible();
	expect(calls(state, 'PUT /api/folders/folder-3')[0].body).toEqual({
		name: 'Documentos',
		parentFolderId: 'folder-4'
	});
});

test('moves the selection with the folder picker and removes photos from the folder', async ({
	page
}) => {
	const state = await open(page, '/folders/folder-1');
	await expect(cells(page)).toHaveCount(20);

	await cells(page)
		.nth(0)
		.click({ modifiers: ['ControlOrMeta'] });
	await cells(page)
		.nth(1)
		.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Mover a otra carpeta' }).click();
	const picker = page.getByRole('dialog', { name: 'Mover a una carpeta' });
	await picker.getByText('Documentos', { exact: true }).click();
	await expect(picker.getByRole('treeitem', { name: 'Documentos' })).toHaveAttribute(
		'aria-selected',
		'true'
	);
	await picker.getByRole('button', { name: 'Mover' }).click();

	await expect(page.getByText('2 movidas a «Documentos»')).toBeVisible();
	await expect(cells(page)).toHaveCount(18);
	await expect(page.getByRole('toolbar')).toBeHidden();
	expect(calls(state, 'POST /api/folders/assets/move')[0].body).toMatchObject({
		sourceFolderId: 'folder-1',
		targetFolderId: 'folder-3'
	});

	await cells(page)
		.nth(0)
		.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Quitar de la carpeta (sin borrar)' }).click();
	// There's no undo for it, so it asks first.
	await page.getByRole('dialog').getByRole('button', { name: 'Quitar de la carpeta' }).click();
	await expect(page.getByText('1 quitada de la carpeta')).toBeVisible();
	await expect(cells(page)).toHaveCount(17);
	expect(calls(state, 'POST /api/folders/assets/remove')).toHaveLength(1);
});

test('the folder picker folds and creates a folder inside the chosen one', async ({ page }) => {
	const state = await open(page, '/folders/folder-1');
	await expect(cells(page)).toHaveCount(20);

	await cells(page)
		.nth(0)
		.click({ modifiers: ['ControlOrMeta'] });
	await page.getByRole('button', { name: 'Mover a otra carpeta' }).click();
	const picker = page.getByRole('dialog', { name: 'Mover a una carpeta' });
	const camera = picker.getByRole('treeitem', { name: 'Camera' });
	// Folded to the top level: subfolders show on demand.
	await expect(picker.getByRole('treeitem', { name: '2025' })).toBeHidden();
	await camera.click();
	await camera.press('ArrowRight');
	await expect(picker.getByRole('treeitem', { name: '2025' })).toBeVisible();
	await camera.press('ArrowLeft');
	await expect(picker.getByRole('treeitem', { name: '2025' })).toBeHidden();

	await expect(picker.getByText('Nueva carpeta dentro de «Camera»')).toBeVisible();
	await picker.getByLabel('Nombre de la carpeta').fill('Viajes');
	await picker.getByRole('button', { name: 'Crear' }).click();
	const created = picker.getByRole('treeitem', { name: 'Viajes' });
	await expect(created).toHaveAttribute('aria-selected', 'true');
	expect(calls(state, 'POST /api/folders')[0].body).toEqual({
		name: 'Viajes',
		parentFolderId: 'folder-1'
	});

	await picker.getByRole('button', { name: 'Mover', exact: true }).click();
	await expect(page.getByText('1 movida a «Viajes»')).toBeVisible();
	expect(calls(state, 'POST /api/folders/assets/move')[0].body).toMatchObject({
		targetFolderId: expect.stringMatching(/^folder-new-/)
	});
});

test('creates a subfolder and opens it', async ({ page }) => {
	const state = await open(page, '/folders/folder-1');

	await page.getByRole('button', { name: 'Nueva subcarpeta' }).click();
	const dialog = page.getByRole('dialog', { name: 'Nueva carpeta' });
	await expect(dialog.getByRole('combobox', { name: 'Ubicación' })).toHaveValue('folder-1');
	await dialog.getByLabel('Nombre').fill('Viajes/2026');
	await expect(dialog.getByText('El nombre no puede llevar')).toBeVisible();
	await expect(dialog.getByRole('button', { name: 'Crear' })).toBeDisabled();
	await dialog.getByLabel('Nombre').fill('Viajes');
	await dialog.getByRole('button', { name: 'Crear' }).click();

	await expect(page.getByRole('heading', { name: 'Viajes', level: 1 })).toBeVisible();
	expect(calls(state, 'POST /api/folders')[0].body).toEqual({
		name: 'Viajes',
		parentFolderId: 'folder-1'
	});
	await expect(item(page, 'Viajes')).toHaveAttribute('aria-selected', 'true');
});

test('renames a folder keeping it where it is', async ({ page }) => {
	const state = await open(page, '/folders/folder-2');

	await page.getByRole('button', { name: 'Más acciones' }).click();
	await page.getByRole('menuitem', { name: 'Renombrar o mover' }).click();
	const dialog = page.getByRole('dialog', { name: 'Renombrar o mover la carpeta' });
	await expect(dialog.getByRole('button', { name: 'Guardar' })).toBeDisabled();
	await dialog.getByLabel('Nombre').fill('Año 2025');
	await dialog.getByRole('button', { name: 'Guardar' }).click();

	await expect(page.getByText('Carpeta actualizada')).toBeVisible();
	expect(calls(state, 'PUT /api/folders/folder-2')[0].body).toEqual({
		name: 'Año 2025',
		parentFolderId: 'folder-1'
	});
	await expect(page.getByRole('heading', { name: 'Año 2025', level: 1 })).toBeVisible();
});

test('deletes a folder after confirming and goes to its parent', async ({ page }) => {
	const state = await open(page, '/folders/folder-2');

	await page.getByRole('button', { name: 'Más acciones' }).click();
	await page.getByRole('menuitem', { name: 'Eliminar carpeta' }).click();
	const confirm = page.getByRole('dialog', { name: '¿Eliminar la carpeta?' });
	await expect(confirm).toContainText('5 elementos irán a la papelera');
	await confirm.getByRole('button', { name: 'Eliminar carpeta' }).click();

	await expect(page).toHaveURL(/\/folders\/folder-1$/);
	expect(calls(state, 'DELETE /api/folders/folder-2')).toHaveLength(1);
	await expect(item(page, '2025')).toBeHidden();
});

test('pins a folder and hides a shared one from my photos', async ({ page }) => {
	const state = await open(page, '/folders/folder-4');

	await page.getByRole('button', { name: 'Fijar en la barra lateral' }).click();
	await expect(page.getByText('«Familia» fijado')).toBeVisible();
	await expect(
		page.getByRole('navigation', { name: 'Navegación principal' }).getByRole('link', {
			name: 'Familia'
		})
	).toBeVisible();

	await page.getByRole('button', { name: 'Más acciones' }).click();
	await expect(page.getByRole('menuitem', { name: 'Eliminar carpeta' })).toBeHidden();
	await page.getByRole('menuitem', { name: 'Ocultar de mis fotos' }).click();
	await expect(page.getByText('Oculta de mis fotos')).toBeVisible();
	expect(calls(state, 'PUT /api/folders/folder-4/discovery-visibility')[0].body).toEqual({
		included: false
	});
});

test('pinned albums and folders share the sidebar; each index shows only its own', async ({
	page
}) => {
	await open(page, '/folders/folder-4');
	await page.getByRole('button', { name: 'Fijar en la barra lateral' }).click();
	await expect(page.getByText('«Familia» fijado')).toBeVisible();

	const sidebar = page.getByRole('navigation', { name: 'Navegación principal' });
	await sidebar.getByRole('link', { name: 'Álbumes' }).click();
	await page.getByRole('button', { name: 'Fijar «Perros»' }).click();
	await expect(sidebar.getByRole('link', { name: 'Perros' })).toBeVisible();
	await expect(sidebar.getByRole('link', { name: 'Familia' })).toBeVisible();

	// The albums page: its pinned albums, smart ones too, and no folder.
	const pinned = page.getByRole('region', { name: 'Fijados' });
	for (const name of [/Vacaciones/, /Perros/])
		await expect(pinned.getByRole('link', { name })).toBeVisible();
	await expect(pinned.getByRole('link')).toHaveCount(2);

	// A filter turns the page into one list of matches: no pinned group.
	await page.getByRole('searchbox', { name: 'Buscar álbumes' }).fill('perr');
	await expect(pinned).toBeHidden();
	await expect(page.getByRole('main').getByRole('link', { name: /Perros/ })).toBeVisible();

	// The folders page: the pinned folder alone, above the top-level ones.
	await sidebar.getByRole('link', { name: 'Carpetas', exact: true }).click();
	await expect(pinned.getByRole('link', { name: /Familia/ })).toBeVisible();
	await expect(pinned.getByRole('link')).toHaveCount(1);
	await expect(page.getByRole('heading', { name: 'Carpetas principales' })).toBeVisible();
});

test('a folder with subfolders and photos folds either section and shares the view', async ({
	page
}) => {
	await open(page, '/folders/folder-1');
	await expect(cells(page)).toHaveCount(20);
	const subfolders = page.getByRole('list', { name: 'Subcarpetas' });
	await expect(subfolders.getByRole('link', { name: /2025/ })).toBeVisible();
	await expect(subfolders).toHaveClass(/grid/);

	// The tiles or rows chosen here are the folders index's too.
	await page.getByRole('radio', { name: 'Lista' }).check();
	await expect(subfolders).toHaveClass(/list/);
	await page
		.getByRole('navigation', { name: 'Navegación principal' })
		.getByRole('link', { name: 'Carpetas', exact: true })
		.click();
	await expect(page.getByRole('list', { name: 'Carpetas principales' })).toHaveClass(/list/);
	await expect(page.getByRole('radio', { name: 'Lista' })).toBeChecked();

	await page.goBack();
	await expect(cells(page)).toHaveCount(20);
	const subfoldersHead = page.getByRole('button', { name: 'Subcarpetas 1' });
	const photosHead = page.getByRole('button', { name: 'Fotos 20' });
	await expect(subfoldersHead).toHaveAttribute('aria-expanded', 'true');
	await expect(photosHead).toHaveAttribute('aria-expanded', 'true');

	await photosHead.click();
	await expect(photosHead).toHaveAttribute('aria-expanded', 'false');
	await expect(cells(page)).toHaveCount(0);
	await expect(subfolders).toBeVisible();
	// Folded, its heading stays right under the subfolders, not at the bottom.
	await expect(photosHead).toBeInViewport();
	const listBottom = await subfolders.evaluate((list) => list.getBoundingClientRect().bottom);
	const headTop = (await photosHead.boundingBox())!.y;
	expect(headTop - listBottom).toBeLessThan(80);

	await subfoldersHead.click();
	await expect(subfolders).toBeHidden();
	await expect(page.getByRole('radio', { name: 'Lista' })).toBeHidden();

	// Folded sections stay folded on the next visit.
	await page.reload();
	await expect(page.getByRole('button', { name: 'Subcarpetas 1' })).toHaveAttribute(
		'aria-expanded',
		'false'
	);
	await expect(page.getByRole('button', { name: 'Fotos 20' })).toHaveAttribute(
		'aria-expanded',
		'false'
	);
	await page.getByRole('button', { name: 'Fotos 20' }).click();
	await expect(cells(page)).toHaveCount(20);

	// A folder without subfolders has nothing to fold: its photos just show.
	await open(page, '/folders/folder-2');
	await expect(cells(page)).toHaveCount(5);
	await expect(page.getByRole('button', { name: /^Fotos/ })).toHaveCount(0);
});

test('shares a folder with a person', async ({ page }) => {
	const state = await open(page, '/folders/folder-1');

	await page.getByRole('button', { name: 'Compartir', exact: true }).click();
	const dialog = page.getByRole('dialog', { name: 'Compartir «Camera»' });
	await expect(dialog.getByRole('tab')).toHaveCount(0);
	await dialog.getByRole('combobox', { name: 'Elige a quién…' }).selectOption('user-luis');
	await dialog.getByRole('button', { name: 'Compartir', exact: true }).click();

	await expect(page.getByText('Compartido con luis')).toBeVisible();
	expect(calls(state, 'POST /api/folders/folder-1/permissions')[0].body).toMatchObject({
		userId: 'user-luis',
		canRead: true,
		canWrite: false
	});
});

test('the tree pane is resized by dragging or with the keyboard, and remembered', async ({
	page
}) => {
	await open(page, '/folders');
	const handle = page.getByRole('separator', { name: 'Cambiar el ancho del panel' });
	const pane = page.locator('#folders-tree');
	await expect(handle).toHaveAttribute('aria-valuenow', '280');

	await handle.focus();
	await page.keyboard.press('ArrowRight');
	await page.keyboard.press('ArrowRight');
	await expect(handle).toHaveAttribute('aria-valuenow', '312');

	const box = (await handle.boundingBox())!;
	await page.mouse.move(box.x + box.width / 2, box.y + 200);
	await page.mouse.down();
	await page.mouse.move(box.x + box.width / 2 + 100, box.y + 200, { steps: 4 });
	await page.mouse.up();
	await expect(handle).toHaveAttribute('aria-valuenow', '412');
	expect(Math.round((await pane.boundingBox())!.width)).toBe(412);

	await page.reload();
	await expect(handle).toHaveAttribute('aria-valuenow', '412');

	await handle.dblclick();
	await expect(handle).toHaveAttribute('aria-valuenow', '280');
});

test('subfolder tiles are resized from inside a folder too', async ({ page }) => {
	await open(page, '/folders/folder-1');
	const slider = page.getByRole('slider', { name: 'Tamaño de las tarjetas' });
	const tile = page.getByRole('list', { name: 'Subcarpetas' }).getByRole('listitem').first();
	const width = async () => (await tile.boundingBox())!.width;
	const before = await width();

	await slider.fill('0');
	await expect.poll(width).toBeLessThan(before);

	// The same size as the folders index.
	await page
		.getByRole('navigation', { name: 'Navegación principal' })
		.getByRole('link', { name: 'Carpetas', exact: true })
		.click();
	await expect(page.getByRole('slider', { name: 'Tamaño de las tarjetas' })).toHaveValue('0');
});

test('with subfolders, the photos size sits in the Fotos section', async ({ page }) => {
	await open(page, '/folders/folder-1');
	await expect(cells(page)).toHaveCount(20);
	const photosSize = page.getByRole('slider', { name: 'Tamaño de las miniaturas' });
	const photosHead = page.getByRole('button', { name: 'Fotos 20' });

	// One for the photos, on the Fotos heading's row, under the subfolders.
	await expect(photosSize).toHaveCount(1);
	const sliderBox = (await photosSize.boundingBox())!;
	const headBox = (await photosHead.boundingBox())!;
	expect(
		Math.abs(sliderBox.y + sliderBox.height / 2 - (headBox.y + headBox.height / 2))
	).toBeLessThan(12);

	const height = async () => (await cells(page).first().boundingBox())!.height;
	const before = await height();
	await photosSize.fill('0');
	await expect.poll(height).toBeLessThan(before);

	// Folded photos take their size control with them.
	await photosHead.click();
	await expect(photosSize).toHaveCount(0);
	await photosHead.click();

	// A folder with only photos keeps it in the toolbar, like any collection.
	await open(page, '/folders/folder-2');
	await expect(cells(page)).toHaveCount(5);
	const top = (await page.getByRole('slider', { name: 'Tamaño de las miniaturas' }).boundingBox())!;
	expect(top.y).toBeLessThan((await page.getByRole('heading', { level: 1 }).boundingBox())!.y + 40);
});
