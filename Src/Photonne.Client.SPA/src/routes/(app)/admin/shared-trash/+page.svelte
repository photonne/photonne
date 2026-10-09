<script lang="ts">
	import {
		getSharedTrash,
		purgeSharedTrash,
		restoreSharedTrash,
		type SharedTrashItemResponse
	} from '#lib/api/index.js';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { errorText } from '#lib/admin/errors.js';
	import { adminIcons } from '#lib/admin/icons.js';
	import { deleters, sharedTrashAsset } from '#lib/admin/shared-trash.js';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { formatBytes } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { ListStore } from '#lib/timeline/list-store.svelte.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import '#lib/admin/admin.css';

	const store = new ListStore({ grouping: 'month', reload: () => load() });

	let all = $state<SharedTrashItemResponse[]>([]);
	let status = $state<'pending' | 'error' | 'ready'>('pending');
	let cursor: string | null = null;
	let hasMore = false;
	let loadingMore = false;
	let who = $state<string | null>(null);
	let busy = $state(false);
	let purging = $state<{ ids: string[]; selection: Selection | null } | null>(null);
	const viewer = new ViewerRoute();

	const people = $derived(deleters(all));
	const totalBytes = $derived(all.reduce((sum, item) => sum + item.fileSize, 0));

	// The grid shows the items of the chosen person (or everyone's).
	$effect(() => {
		store.items = all
			.filter((item) => who === null || (item.deletedByUsername ?? '') === who)
			.map(sharedTrashAsset);
	});

	async function load() {
		status = 'pending';
		cursor = null;
		const page = await fetchPage();
		if (!page) {
			status = 'error';
			return;
		}
		all = page;
		status = 'ready';
	}

	async function more() {
		if (!hasMore || loadingMore) return;
		loadingMore = true;
		const page = await fetchPage();
		loadingMore = false;
		if (page) all = [...all, ...page];
	}

	async function fetchPage() {
		const { data } = await getSharedTrash({
			query: { pageSize: 200, cursor: cursor ?? undefined }
		});
		if (!data) return null;
		hasMore = data.hasMore;
		cursor = data.nextCursor ?? null;
		return data.items;
	}

	load();

	/**
	 * Takes the items off the page. From the viewer (no selection), it first
	 * moves on to the next photo, or closes when there is none.
	 */
	async function drop(ids: readonly string[], selection: Selection | null) {
		if (!selection && viewer.openId) {
			const { previous, next } = neighborsIn(store.order, viewer.openId);
			const target = next ?? previous;
			if (target) await viewer.navigate(target);
			else await viewer.close();
		}
		const gone = new Set(ids);
		all = all.filter((item) => !gone.has(item.id));
		selection?.set(ids, false);
		if (who !== null && !all.some((item) => (item.deletedByUsername ?? '') === who)) who = null;
	}

	async function restore(ids: string[], selection: Selection | null) {
		busy = true;
		const { error } = await restoreSharedTrash({ body: { assetIds: ids } });
		busy = false;
		if (error) {
			toasts.error(errorText(error));
			return;
		}
		drop(ids, selection);
		toasts.show(m.admin_strash_restored({ count: ids.length }));
	}

	async function purge() {
		if (!purging) return;
		const { ids, selection } = purging;
		busy = true;
		const { error } = await purgeSharedTrash({ body: { assetIds: ids } });
		busy = false;
		purging = null;
		if (error) {
			toasts.error(errorText(error));
			return;
		}
		drop(ids, selection);
		toasts.show(m.admin_strash_purged({ count: ids.length }));
	}
</script>

<div class="admin-page shared-trash">
	{#if status === 'ready' && all.length === 0}
		<!-- Nothing to review: say so the way every empty page does. -->
		<PageHeader title={m.admin_shared_trash()} subtitle={m.admin_strash_description()}>
			{#snippet actions()}
				<button type="button" class="btn" onclick={load}>
					<Icon name="refresh" size={18} />
					{m.admin_core_refresh()}
				</button>
			{/snippet}
		</PageHeader>
		<EmptyState icon="restore" title={m.admin_strash_empty()} hint={m.admin_strash_empty_hint()} />
	{:else}
		<CollectionView
			{store}
			title={m.admin_shared_trash()}
			{status}
			emptyText={m.admin_strash_empty()}
			viewerActions={[]}
			onnearend={more}
		>
			{#snippet toolbar()}
				<button type="button" class="btn" onclick={load} disabled={status === 'pending'}>
					<Icon name="refresh" size={18} />
					{m.admin_core_refresh()}
				</button>
			{/snippet}
			{#snippet header()}
				<div class="intro">
					<p class="muted">{m.admin_strash_description()}</p>
					{#if all.length}
						<div class="who" role="group" aria-label={m.admin_strash_filter()}>
							<span class="small muted">
								{m.admin_strash_summary({ count: all.length, size: formatBytes(totalBytes) })}
							</span>
							<button
								type="button"
								class="chip"
								aria-pressed={who === null}
								onclick={() => (who = null)}>{m.admin_strash_everyone()}</button
							>
							{#each people as person (person.name)}
								<button
									type="button"
									class="chip"
									aria-pressed={who === person.name}
									onclick={() => (who = person.name)}
								>
									{person.name || m.admin_strash_unknown()}
									<span class="num">{person.count}</span>
								</button>
							{/each}
						</div>
					{/if}
				</div>
			{/snippet}
			{#snippet viewerExtra(assetId)}
				<button
					type="button"
					class="viewer-action"
					disabled={busy}
					aria-label={m.admin_strash_restore()}
					title={m.admin_strash_restore()}
					onclick={() => restore([assetId], null)}
				>
					<Icon name="restore" />
				</button>
				<button
					type="button"
					class="viewer-action"
					disabled={busy}
					aria-label={m.admin_strash_purge()}
					title={m.admin_strash_purge()}
					onclick={() => (purging = { ids: [assetId], selection: null })}
				>
					<Icon path={adminIcons.deleteForever} />
				</button>
			{/snippet}
			{#snippet selectionActions(selection)}
				<button
					type="button"
					class="action"
					disabled={busy}
					onclick={() => restore([...selection.ids], selection)}
				>
					<Icon name="restore" />
					<span>{m.admin_strash_restore()}</span>
				</button>
				<button
					type="button"
					class="action danger"
					disabled={busy}
					onclick={() => (purging = { ids: [...selection.ids], selection })}
				>
					<Icon path={adminIcons.deleteForever} />
					<span>{m.admin_strash_purge()}</span>
				</button>
			{/snippet}
		</CollectionView>
	{/if}

	<ConfirmDialog
		danger
		open={purging !== null}
		title={m.admin_strash_purge_title()}
		confirmLabel={m.admin_strash_purge()}
		{busy}
		onconfirm={purge}
		onclose={() => (purging = null)}
	>
		<p>{m.admin_strash_purge_body({ count: purging?.ids.length ?? 0 })}</p>
	</ConfirmDialog>
</div>

<svelte:head>
	<title>{m.admin_shared_trash()} · {m.app_name()}</title>
</svelte:head>

<style>
	.shared-trash {
		height: 100%;
	}

	.intro {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-4) var(--space-2);
	}

	p {
		margin: 0;
	}

	.who {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.chip .num {
		color: var(--color-text-muted);
	}

	.action {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		font-size: var(--font-size-sm);
		font-weight: 500;
		cursor: pointer;
	}

	.action:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.action.danger {
		color: var(--color-danger);
	}

	.viewer-action {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: inherit;
		cursor: pointer;
	}

	.viewer-action:hover:not(:disabled) {
		background: rgb(255 255 255 / 0.12);
	}

	.viewer-action:disabled,
	.action:disabled {
		opacity: 0.5;
		cursor: default;
	}
</style>
