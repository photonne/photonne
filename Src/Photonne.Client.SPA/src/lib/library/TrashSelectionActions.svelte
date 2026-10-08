<script lang="ts">
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import type { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { icons } from './icons.js';
	import type { LibraryActions } from './library-actions.svelte.js';
	import ToolButton from './ToolButton.svelte';

	interface Props {
		selection: Selection;
		batch: BatchActions;
		actions: LibraryActions;
		/** The user's own trash or the shared folders' one. */
		scope: 'personal' | 'shared';
		/** Restore offers Undo only while the server keeps a trash to go back to. */
		undoable: boolean;
	}

	let { selection, batch, actions, scope, undoable }: Props = $props();

	let confirming = $state(false);

	function restore() {
		if (scope === 'shared') actions.restoreShared(selection);
		else actions.restore(selection, undoable);
	}

	function purge() {
		if (scope === 'shared') actions.purgeShared(selection);
		else actions.purge(selection);
	}

	// Delete asks to delete for good; R restores. Same rules as the timeline's
	// shortcuts: not while typing or with a dialog up.
	function onkeydown(event: KeyboardEvent) {
		if (!selection.active || confirming || event.defaultPrevented) return;
		const target = event.target as HTMLElement;
		if (target.closest('input, textarea, select, dialog, [role="dialog"]')) return;
		if (event.ctrlKey || event.metaKey || event.altKey) return;
		if (event.key === 'Delete' || event.key === 'Backspace') {
			event.preventDefault();
			confirming = true;
		} else if (event.key === 'r') {
			event.preventDefault();
			restore();
		}
	}
</script>

<svelte:window {onkeydown} />

<ToolButton
	variant="icon"
	label={m.trash_restore()}
	icon="restore"
	disabled={actions.busy}
	onclick={restore}
/>
{#if scope === 'personal'}
	<BatchActionBar actions={batch} {selection} available={['download']} />
{/if}
<ToolButton
	variant="icon"
	danger
	label={m.trash_purge()}
	icon={{ path: icons.deleteForever }}
	disabled={actions.busy}
	onclick={() => (confirming = true)}
/>

<ConfirmDialog
	closeOnConfirm
	open={confirming}
	danger
	title={m.trash_purge()}
	message={m.trash_purge_confirm({ count: selection.size })}
	confirmLabel={m.trash_purge_action()}
	onclose={() => (confirming = false)}
	onconfirm={purge}
/>
