import {
	archiveAssets,
	deleteAssets,
	purgeAssets,
	purgeSharedTrash,
	restoreAssets,
	restoreSharedTrash,
	unarchiveAssets
} from '#lib/api/index.js';
import { toasts } from '#lib/components/toasts.svelte.js';
import { m } from '#lib/paraglide/messages.js';
import type { GridHost } from '#lib/timeline/grid-host.js';
import type { Selection } from '#lib/timeline/selection.svelte.js';

/** The selection (whose photos it acts on) or explicit ids. */
type Target = Selection | readonly string[];

type Call = (options: { body: { assetIds: string[] } }) => Promise<{ error?: unknown }>;

/**
 * The actions of the archive and the trash on the selected photos (pass the
 * selection, they leave it) or on the one open in the viewer. Like
 * BatchActions, they take the photos out of view at once; the reversible ones
 * offer Undo, and one runs at a time.
 */
export class LibraryActions {
	busy = $state(false);
	#host: GridHost;

	constructor(host: GridHost) {
		this.#host = host;
	}

	/** Back to the timeline; Undo archives them again. */
	unarchive(target: Target) {
		return this.#apply(target, unarchiveAssets, m.archive_unarchived, archiveAssets);
	}

	/**
	 * Out of the trash; Undo sends them back. Without a server trash, Undo
	 * would delete for good, so it isn't offered then.
	 */
	restore(target: Target, undoable = true) {
		return this.#apply(
			target,
			restoreAssets,
			m.trash_restored,
			undoable ? deleteAssets : undefined
		);
	}

	/** Deleted for good: the caller confirms first. */
	purge(target: Target) {
		return this.#apply(target, purgeAssets, m.trash_purged);
	}

	restoreShared(target: Target) {
		return this.#apply(target, restoreSharedTrash, m.trash_restored);
	}

	purgeShared(target: Target) {
		return this.#apply(target, purgeSharedTrash, m.trash_purged);
	}

	#apply(
		target: Target,
		call: Call,
		message: (inputs: { count: number }) => string,
		revert?: Call
	) {
		const selection = 'ids' in target ? target : null;
		const assetIds = [...(selection ? selection.ids : (target as readonly string[]))];
		if (this.busy || assetIds.length === 0) return Promise.resolve();
		this.busy = true;
		return (async () => {
			try {
				const { error } = await call({ body: { assetIds } });
				if (error) throw error;
				this.#host.remove(assetIds);
				selection?.set(assetIds, false);
				toasts.show(message({ count: assetIds.length }), {
					action: revert && {
						label: m.action_undo(),
						run: async () => {
							const { error } = await revert({ body: { assetIds } });
							if (error) toasts.error(m.action_failed());
							else this.#host.reload();
						}
					}
				});
			} catch {
				toasts.error(m.action_failed());
			} finally {
				this.busy = false;
			}
		})();
	}
}
