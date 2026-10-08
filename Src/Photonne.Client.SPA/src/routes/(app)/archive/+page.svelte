<script lang="ts">
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import { getArchived, unarchiveAll } from '#lib/api/index.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import ConfirmDialog from '#lib/library/ConfirmDialog.svelte';
	import { icons } from '#lib/library/icons.js';
	import { LibraryActions } from '#lib/library/library-actions.svelte.js';
	import ToolButton from '#lib/library/ToolButton.svelte';
	import { leaveViewerThen } from '#lib/library/viewer-flow.js';
	import ViewerExtraButtons from '#lib/library/ViewerExtraButtons.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	const list = new PagedList(
		async (cursor) =>
			(await getArchived({ query: { pageSize: 200, cursor: cursor ?? undefined } })).data
	);
	list.start();

	const actions = new LibraryActions(list.store);
	const viewer = new ViewerRoute();
	let confirmingAll = $state(false);
	let unarchivingAll = $state(false);

	async function unarchiveEverything() {
		unarchivingAll = true;
		try {
			const { error } = await unarchiveAll();
			if (error) throw error;
			toasts.show(m.archive_unarchived_all());
			await list.start();
		} catch {
			toasts.error(m.action_failed());
		} finally {
			unarchivingAll = false;
		}
	}
</script>

<svelte:head>
	<title>{m.nav_archive()} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	title={m.nav_archive()}
	status={list.status}
	emptyText={m.archive_empty()}
	viewerActions={['trash', 'album']}
	onnearend={() => list.more()}
>
	{#snippet toolbar()}
		<ToolButton
			label={m.archive_unarchive_all()}
			icon={{ path: icons.unarchive }}
			disabled={list.status !== 'ready' || list.store.items.length === 0 || unarchivingAll}
			onclick={() => (confirmingAll = true)}
		/>
	{/snippet}
	{#snippet header()}
		<p class="hint">{m.archive_hint()}</p>
	{/snippet}
	{#snippet selectionActions(selection, batch)}
		<ToolButton
			variant="icon"
			label={m.archive_unarchive()}
			icon={{ path: icons.unarchive }}
			disabled={actions.busy || batch.busy}
			onclick={() => actions.unarchive(selection)}
		/>
		<BatchActionBar
			actions={batch}
			{selection}
			available={['favorite', 'album', 'folder', 'download', 'trash']}
		/>
	{/snippet}
	{#snippet viewerExtra()}
		<ViewerExtraButtons
			actions={[
				{
					label: m.archive_unarchive(),
					icon: { path: icons.unarchive },
					disabled: actions.busy,
					run: () => leaveViewerThen(viewer, list.store.order, (ids) => actions.unarchive(ids))
				}
			]}
		/>
	{/snippet}
</CollectionView>

<ConfirmDialog
	open={confirmingAll}
	title={m.archive_unarchive_all()}
	message={m.archive_unarchive_all_confirm()}
	confirmLabel={m.archive_unarchive_all()}
	onclose={() => (confirmingAll = false)}
	onconfirm={unarchiveEverything}
/>

<style>
	.hint {
		margin: var(--space-1) 0 0;
		padding: 0 var(--space-4);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
