import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { peopleState } from './fakes/people';
import { TEXT_PHOTO } from './fakes/viewer';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

test('Shift+F draws the face boxes and one opens its face in the panel', async ({ page }) => {
	await fakeApi(page, { signedIn: true });
	await page.goto(`/?asset=${TEXT_PHOTO}`);
	const viewer = page.getByRole('dialog', { name: 'IMG_202609_3.jpg' });
	await expect(viewer).toBeVisible();

	await page.keyboard.press('Shift+F');
	// The toggle lives in the "⋮" menu; the key works with it closed.
	await viewer.getByRole('button', { name: 'Más acciones' }).click();
	await expect(
		viewer.getByRole('menuitemcheckbox', { name: 'Recuadros de caras' })
	).toHaveAttribute('aria-checked', 'true');
	await page.keyboard.press('Escape');
	await expect(viewer.getByRole('menu')).toBeHidden();
	await expect(viewer).toBeVisible();
	const boxes = viewer.getByRole('list', { name: 'Recuadros de caras' });
	await expect(boxes.getByRole('button', { name: 'Cara: Ana' })).toBeVisible();
	await expect(boxes.getByRole('button', { name: 'Cara: Sin asignar' })).toBeVisible();

	// The boxes follow the zoom: zoomed in, a box grows with the photo.
	const box = boxes.getByRole('button', { name: 'Cara: Ana' });
	const before = (await box.boundingBox())!;
	await page.keyboard.press('+');
	await expect
		.poll(async () => (await box.boundingBox())!.width)
		.toBeGreaterThan(before.width * 1.3);
	await page.keyboard.press('0');

	await boxes.getByRole('button', { name: 'Cara: Sin asignar' }).click();
	const panel = page.getByRole('complementary', { name: 'Información' });
	await expect(panel.getByRole('tab', { name: 'Personas' })).toHaveAttribute(
		'aria-selected',
		'true'
	);
	await expect(
		panel.getByRole('button', { name: 'Señalar la cara en la foto' }).nth(1)
	).toHaveAttribute('aria-pressed', 'true');

	await page.keyboard.press('Shift+F');
	await expect(boxes).toBeHidden();
});

test('assigns a face to a new person, unassigns and rejects', async ({ page }) => {
	const api = await fakeApi(page, { signedIn: true });
	await page.goto(`/?asset=${TEXT_PHOTO}`);
	await expect(page.getByRole('dialog', { name: 'IMG_202609_3.jpg' })).toBeVisible();
	await page.keyboard.press('i');
	const panel = page.getByRole('complementary', { name: 'Información' });
	await panel.getByRole('tab', { name: 'Personas' }).click();

	const faces = panel.getByRole('list', { name: 'Caras en la foto' });
	await expect(faces.getByRole('listitem')).toHaveCount(2);
	await expect(faces.getByRole('listitem').first()).toContainText('Ana');
	await expect(faces.getByRole('listitem').nth(1)).toContainText('Sin asignar');

	// Assign the unknown face to someone new.
	await faces
		.getByRole('listitem')
		.nth(1)
		.getByRole('button', { name: 'Asignar a una persona' })
		.click();
	const picker = page.getByRole('dialog', { name: 'Asignar a una persona' });
	await picker.getByRole('searchbox').fill('Pedro');
	await picker.getByRole('button', { name: 'Nueva persona: «Pedro»' }).click();
	await expect(page.getByText('Cara asignada a Pedro')).toBeVisible();
	await expect(faces.getByRole('listitem').nth(1)).toContainText('Pedro');
	const people = peopleState(api.state);
	expect(people.people.some((p) => p.name === 'Pedro')).toBe(true);

	// Ana's face is not Ana after all.
	await faces
		.getByRole('listitem')
		.first()
		.getByRole('button', { name: 'No es esta persona' })
		.click();
	await expect(page.getByText('Se ha quitado la persona de la cara')).toBeVisible();
	await expect(faces.getByRole('listitem').first()).toContainText('Sin asignar');

	// And the other one isn't a face at all.
	await faces.getByRole('listitem').nth(1).getByRole('button', { name: 'No es una cara' }).click();
	await expect(page.getByText('Marcada como «no es una cara»')).toBeVisible();
	await expect(faces.getByRole('listitem')).toHaveCount(1);
	expect(people.faces.find((f) => f.id === 'face-viewer-unknown')?.rejected).toBe(true);

	// Escape in the panel still closes the viewer, not a stale dialog.
	await page.keyboard.press('Escape');
	await expect(page.getByRole('dialog')).toBeHidden();
});
