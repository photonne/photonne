<script lang="ts">
	import { page } from '$app/state';
	import Icon, { type IconName } from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import AlbumPickerDialog from './AlbumPickerDialog.svelte';
	import type { BatchActions } from './batch-actions.svelte.js';
	import FolderPickerDialog from './FolderPickerDialog.svelte';

	type Action = 'favorite' | 'album' | 'folder' | 'share' | 'download' | 'archive' | 'trash';

	interface Props {
		actions: BatchActions;
		selection: Selection;
		/**
		 * Which actions this surface offers (archive makes no sense in the
		 * archive). `share` needs the host to render ShareAssetsDialog for
		 * `actions.sharing` (CollectionView and the timeline do).
		 */
		available?: readonly Action[];
	}

	let {
		actions,
		selection,
		available = ['favorite', 'album', 'folder', 'share', 'download', 'archive', 'trash']
	}: Props = $props();

	let picking = $state<'album' | 'folder' | null>(null);

	const buttons: Record<Action, { icon: IconName; label: () => string; run: () => void }> = {
		favorite: {
			icon: 'favoriteOutline',
			label: m.action_favorite,
			run: () => actions.toggleFavorites()
		},
		album: { icon: 'albumAdd', label: m.action_add_to_album, run: () => (picking = 'album') },
		folder: { icon: 'folder', label: m.action_move_to_folder, run: () => (picking = 'folder') },
		share: { icon: 'share', label: m.links_share_action, run: () => actions.share() },
		download: { icon: 'download', label: m.action_download, run: () => actions.download() },
		archive: { icon: 'archive', label: m.action_archive, run: () => actions.archive() },
		trash: { icon: 'delete', label: m.action_trash, run: () => actions.trash() }
	};

	const shortcuts: Partial<Record<Action, string>> = { favorite: 'F', share: 'S', trash: 'Delete' };

	// Shortcuts while photos are selected and no dialog or text field has focus.
	function onkeydown(event: KeyboardEvent) {
		if (!selection.active || picking || actions.sharing || event.defaultPrevented) return;
		// The viewer over the grid has its own keys (f, s, Delete) for the photo shown.
		if (page.url.searchParams.has('asset')) return;
		const target = event.target as HTMLElement;
		if (target.closest('input, textarea, select, dialog, [role="dialog"]')) return;
		if (event.ctrlKey || event.metaKey || event.altKey) return;
		if ((event.key === 'Delete' || event.key === 'Backspace') && available.includes('trash')) {
			event.preventDefault();
			actions.trash();
		} else if (event.key === 'f' && available.includes('favorite')) {
			event.preventDefault();
			actions.toggleFavorites();
		} else if (event.key === 's' && available.includes('share')) {
			event.preventDefault();
			actions.share();
		}
	}
</script>

<svelte:window {onkeydown} />

{#each available as key (key)}
	{@const button = buttons[key]}
	<button
		type="button"
		class="action"
		title={button.label()}
		aria-label={button.label()}
		aria-keyshortcuts={shortcuts[key]}
		disabled={actions.busy || (key === 'download' && actions.downloader.busy)}
		onclick={button.run}
	>
		<Icon name={button.icon} />
	</button>
{/each}

<AlbumPickerDialog
	open={picking === 'album'}
	onclose={() => (picking = null)}
	onpick={(album) => {
		picking = null;
		actions.addToAlbum(album);
	}}
/>
<FolderPickerDialog
	open={picking === 'folder'}
	onclose={() => (picking = null)}
	onpick={(folder, byYear) => {
		picking = null;
		actions.moveToFolder(folder, byYear);
	}}
/>

<style>
	.action {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.action:hover {
		background: var(--color-surface);
	}

	.action:disabled {
		opacity: 0.4;
		cursor: progress;
	}
</style>
