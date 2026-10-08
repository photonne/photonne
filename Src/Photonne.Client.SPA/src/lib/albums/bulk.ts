export interface BulkOutcome {
	succeeded: string[];
	failed: string[];
}

/**
 * Runs `action` on each id, one at a time: the server has no batch delete,
 * leave or move for albums and folders, and one at a time keeps a folder
 * move from racing its own parent's. A failure doesn't stop the rest.
 */
export async function runBulk(
	ids: readonly string[],
	action: (id: string) => Promise<boolean>
): Promise<BulkOutcome> {
	const outcome: BulkOutcome = { succeeded: [], failed: [] };
	for (const id of ids) {
		let ok: boolean;
		try {
			ok = await action(id);
		} catch {
			ok = false;
		}
		(ok ? outcome.succeeded : outcome.failed).push(id);
	}
	return outcome;
}

/**
 * What a click on a selectable card does, file-manager style: Shift extends
 * from the anchor, Ctrl/Cmd toggles, and once something is selected a plain
 * click toggles too; otherwise it follows the link.
 */
export function cardClick(
	event: Pick<MouseEvent, 'shiftKey' | 'ctrlKey' | 'metaKey'>,
	selecting: boolean
): 'range' | 'toggle' | 'open' {
	if (event.shiftKey) return 'range';
	if (event.ctrlKey || event.metaKey || selecting) return 'toggle';
	return 'open';
}
