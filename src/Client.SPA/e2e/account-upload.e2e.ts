import { createHash } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { fakeApi } from './fake-api';
import { accountState } from './fakes/account';

test.use({ locale: 'es-ES', viewport: { width: 1280, height: 800 } });

const jpeg = (seed: string) => Buffer.from(`fake-jpeg-${seed}`);
const sha256 = (buffer: Buffer) => createHash('sha256').update(buffer).digest('hex');

test('uploads picked files, skips duplicates and non-media, and keeps the dates', async ({
	page
}) => {
	const { state } = await fakeApi(page, { signedIn: true });
	const account = accountState(state);
	account.existingChecksums.push(sha256(jpeg('old')));

	await page.goto('/upload');
	await expect(page.getByRole('heading', { name: 'Subir', level: 1 })).toBeVisible();

	await page.locator('input[type="file"][multiple]').setInputFiles([
		{ name: 'nueva.jpg', mimeType: 'image/jpeg', buffer: jpeg('new') },
		{ name: 'vieja.jpg', mimeType: 'image/jpeg', buffer: jpeg('old') },
		{ name: 'notas.txt', mimeType: 'text/plain', buffer: Buffer.from('hola') }
	]);

	await expect(page.getByText('1 archivo ignorado: no es una foto ni un vídeo')).toBeVisible();
	const queue = page.getByRole('list', { name: 'Cola de subida' });
	await expect(queue.getByRole('listitem')).toHaveCount(2);
	await expect(queue.getByRole('listitem').filter({ hasText: 'vieja.jpg' })).toContainText(
		'Ya estaba en tu biblioteca'
	);
	await expect(queue.getByRole('listitem').filter({ hasText: 'nueva.jpg' })).toContainText(
		'Subida'
	);
	expect(account.uploaded).toEqual(['nueva.jpg']);
	expect(Number((state.lastUploadDates as Record<string, string>).fileCreatedAt)).toBeGreaterThan(
		0
	);

	// The batch summary offers the next steps.
	const summary = page.getByRole('status').filter({ hasText: '1 foto o vídeo subido' });
	await expect(summary.getByRole('link', { name: 'Ver en Fotos' })).toBeVisible();
	await summary.getByRole('button', { name: 'Añadir a un álbum' }).click();
	await page
		.getByRole('dialog')
		.getByRole('button', { name: /Vacaciones/ })
		.click();
	await expect(page.getByText('1 añadida a «Vacaciones»')).toBeVisible();

	await page.getByRole('button', { name: 'Limpiar terminadas' }).click();
	await expect(queue).toHaveCount(0);
});

test('a failed upload can be retried', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });
	const account = accountState(state);
	account.failOnce.push('fallo.jpg');

	await page.goto('/upload');
	await page
		.locator('input[type="file"][multiple]')
		.setInputFiles([{ name: 'fallo.jpg', mimeType: 'image/jpeg', buffer: jpeg('fail') }]);

	const row = page.getByRole('listitem').filter({ hasText: 'fallo.jpg' });
	await expect(row).toContainText('Error del servidor');
	await expect(page.getByText('1 con error')).toBeVisible();

	await row.getByRole('button', { name: 'Reintentar fallo.jpg' }).click();
	await expect(row).toContainText('Subida');
	expect(account.uploaded).toEqual(['fallo.jpg']);
});

test('files dropped on the drop zone are queued', async ({ page }) => {
	const { state } = await fakeApi(page, { signedIn: true });

	await page.goto('/upload');
	const zone = page.getByRole('region', { name: 'Arrastra aquí fotos, vídeos o carpetas' });
	await expect(zone).toBeVisible();

	await zone.evaluate((element) => {
		const transfer = new DataTransfer();
		transfer.items.add(new File(['a'], 'arrastrada.heic', { type: 'image/heic' }));
		for (const type of ['dragenter', 'dragover', 'drop']) {
			element.dispatchEvent(
				new DragEvent(type, { dataTransfer: transfer, bubbles: true, cancelable: true })
			);
		}
	});

	await expect(page.getByRole('listitem').filter({ hasText: 'arrastrada.heic' })).toContainText(
		'Subida'
	);
	expect(accountState(state).uploaded).toEqual(['arrastrada.heic']);
});
