/**
 * Copies text. The Clipboard API only exists in secure contexts (https or
 * localhost), and a self-hosted server is often opened as plain http on the
 * local network; there the old copy command still works from a click.
 */
export async function copyText(text: string): Promise<void> {
	if (navigator.clipboard?.writeText) {
		await navigator.clipboard.writeText(text);
		return;
	}
	const area = document.createElement('textarea');
	area.value = text;
	area.setAttribute('readonly', '');
	area.style.position = 'fixed';
	area.style.opacity = '0';
	// Inside an open modal <dialog> when there is one: the rest of the page is inert.
	const host = document.querySelector('dialog[open]') ?? document.body;
	const focused = document.activeElement as HTMLElement | null;
	host.append(area);
	area.select();
	try {
		if (!document.execCommand('copy')) throw new Error('Copy command refused');
	} finally {
		area.remove();
		focused?.focus();
	}
}
