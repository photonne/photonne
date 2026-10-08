<script lang="ts">
	import { page } from '$app/state';
	import { emptyTrash, getSharedTrash, getTrashedAssets, restoreAllTrash } from '#lib/api/index.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import ConfirmDialog from '#lib/library/ConfirmDialog.svelte';
	import { icons } from '#lib/library/icons.js';
	import { LibraryActions } from '#lib/library/library-actions.svelte.js';
	import { sharedTrashToTimeline } from '#lib/library/pages.js';
	import ToolButton from '#lib/library/ToolButton.svelte';
	import { useTrashPolicy } from '#lib/library/trash-settings.svelte.js';
	import TrashSelectionActions from '#lib/library/TrashSelectionActions.svelte';
	import { leaveViewerThen } from '#lib/library/viewer-flow.js';
	import ViewerExtraButtons from '#lib/library/ViewerExtraButtons.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	// Both come in the order they were deleted, newest first. A month header
	// heads each run of photos from the same capture month (what was deleted
	// together usually is); the shared trash has no capture date and goes by
	// the month it was deleted in.
	const personal = new PagedList(
		async (cursor) =>
			(await getTrashedAssets({ query: { pageSize: 200, cursor: cursor ?? undefined } })).data
	);
	const shared = new PagedList(async (cursor) => {
		const { data } = await getSharedTrash({
			query: { pageSize: 200, cursor: cursor ?? undefined }
		});
		return data && { ...data, items: data.items.map(sharedTrashToTimeline) };
	});
	personal.start();
	shared.start();

	const policy = useTrashPolicy();
	const viewer = new ViewerRoute();
	const personalActions = new LibraryActions(personal.store);
	const sharedActions = new LibraryActions(shared.store);

	const scope = $derived<'personal' | 'shared'>(
		page.url.searchParams.get('scope') === 'shared' ? 'shared' : 'personal'
	);
	const list = $derived(scope === 'shared' ? shared : personal);
	const actions = $derived(scope === 'shared' ? sharedActions : personalActions);
	// The shared trash only concerns those who administer a shared folder;
	// the switch shows when there's something in it (or it's the open one).
	const showScopes = $derived(scope === 'shared' || shared.store.items.length > 0);
	const isEmpty = $derived(list.status !== 'ready' || list.store.items.length === 0);

	let confirming = $state<'restoreAll' | 'empty' | 'purgeOpen' | null>(null);
	let bulkBusy = $state(false);

	async function bulk(call: () => Promise<{ error?: unknown }>, done: string) {
		bulkBusy = true;
		try {
			const { error } = await call();
			if (error) throw error;
			toasts.show(done);
			await personal.start();
		} catch {
			toasts.error(m.action_failed());
		} finally {
			bulkBusy = false;
		}
	}

	function restoreOpen() {
		leaveViewerThen(viewer, list.store.order, (ids) =>
			scope === 'shared' ? actions.restoreShared(ids) : actions.restore(ids, policy.current.enabled)
		);
	}

	function purgeOpen() {
		leaveViewerThen(viewer, list.store.order, (ids) =>
			scope === 'shared' ? actions.purgeShared(ids) : actions.purge(ids)
		);
	}

	function retentionText() {
		const { enabled, retentionDays, maxQuotaMb } = policy.current;
		if (!enabled) return m.trash_policy_disabled();
		const parts = [
			retentionDays
				? m.trash_policy_retention({ days: retentionDays })
				: m.trash_policy_keep_until_emptied()
		];
		if (maxQuotaMb) parts.push(m.trash_policy_quota({ size: formatQuota(maxQuotaMb) }));
		return parts.join(' ');
	}

	function formatQuota(mb: number) {
		return mb >= 1024 ? `${Math.round((mb / 1024) * 10) / 10} GB` : `${mb} MB`;
	}
</script>

<svelte:head>
	<title>{m.nav_trash()} · {m.app_name()}</title>
</svelte:head>

{#key scope}
	<CollectionView
		store={list.store}
		title={m.nav_trash()}
		status={list.status}
		emptyText={scope === 'shared' ? m.trash_shared_empty() : m.trash_empty()}
		viewerActions={[]}
		onnearend={() => list.more()}
	>
		{#snippet toolbar()}
			{#if scope === 'personal'}
				<ToolButton
					label={m.trash_restore_all()}
					icon="restore"
					disabled={isEmpty || bulkBusy}
					onclick={() => (confirming = 'restoreAll')}
				/>
				<ToolButton
					label={m.trash_empty_action()}
					icon={{ path: icons.deleteForever }}
					danger
					disabled={isEmpty || bulkBusy}
					onclick={() => (confirming = 'empty')}
				/>
			{/if}
		{/snippet}
		{#snippet header()}
			<div class="header">
				{#if showScopes}
					<nav class="scopes" aria-label={m.trash_scope()}>
						<a
							href={appHref('/trash')}
							aria-current={scope === 'personal' ? 'page' : undefined}
							data-sveltekit-replacestate
						>
							{m.trash_scope_personal()}
						</a>
						<a
							href={`${appHref('/trash')}?scope=shared`}
							aria-current={scope === 'shared' ? 'page' : undefined}
							data-sveltekit-replacestate
						>
							{m.trash_scope_shared()}
						</a>
					</nav>
				{/if}
				<p class="policy" role="note">
					{scope === 'shared' ? m.trash_shared_hint() : retentionText()}
				</p>
			</div>
		{/snippet}
		{#snippet selectionActions(selection, batch)}
			<TrashSelectionActions
				{selection}
				{batch}
				{actions}
				{scope}
				undoable={policy.current.enabled}
			/>
		{/snippet}
		{#snippet viewerExtra()}
			<ViewerExtraButtons
				actions={[
					{
						label: m.trash_restore(),
						icon: { name: 'restore' },
						disabled: actions.busy,
						run: restoreOpen
					},
					{
						label: m.trash_purge(),
						icon: { path: icons.deleteForever },
						disabled: actions.busy,
						run: () => (confirming = 'purgeOpen')
					}
				]}
			/>
		{/snippet}
	</CollectionView>
{/key}

<ConfirmDialog
	open={confirming === 'restoreAll'}
	title={m.trash_restore_all()}
	message={m.trash_restore_all_confirm()}
	confirmLabel={m.trash_restore_all()}
	onclose={() => (confirming = null)}
	onconfirm={() => bulk(() => restoreAllTrash(), m.trash_restored_all())}
/>
<ConfirmDialog
	open={confirming === 'empty'}
	danger
	title={m.trash_empty_action()}
	message={m.trash_empty_confirm()}
	confirmLabel={m.trash_empty_action()}
	onclose={() => (confirming = null)}
	onconfirm={() => bulk(() => emptyTrash(), m.trash_emptied())}
/>
<ConfirmDialog
	open={confirming === 'purgeOpen'}
	danger
	title={m.trash_purge()}
	message={m.trash_purge_confirm({ count: 1 })}
	confirmLabel={m.trash_purge_action()}
	onclose={() => (confirming = null)}
	onconfirm={purgeOpen}
/>

<style>
	.header {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-4) 0;
	}

	.scopes {
		display: flex;
		gap: var(--space-1);
	}

	.scopes a {
		padding: var(--space-1) var(--space-3);
		border-radius: 999px;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	.scopes a:hover {
		background: var(--color-surface);
	}

	.scopes a[aria-current='page'] {
		background: var(--color-surface);
		color: var(--color-text);
		font-weight: 600;
	}

	.policy {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
