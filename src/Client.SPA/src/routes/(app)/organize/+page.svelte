<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { tick } from 'svelte';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import FolderPickerDialog from '#lib/actions/FolderPickerDialog.svelte';
	import {
		getOrganizeInbox,
		getOrganizeInboxItems,
		type OrganizeSuggestionResponse
	} from '#lib/api/index.js';
	import { getOrganizeSuggestionsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import OrganizeNav from '#lib/search/organize/OrganizeNav.svelte';
	import {
		movedMessage,
		moveToFolder,
		refreshOrganize,
		setExcluded
	} from '#lib/search/organize/organize.js';
	import { suggestionTitle } from '#lib/search/organize/suggestion.js';
	import Suggestions from '#lib/search/organize/Suggestions.svelte';
	import { hidePath } from '#lib/search/icons.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import { Selection } from '#lib/timeline/selection.svelte.js';

	const queryClient = useQueryClient();
	const suggestions = createQuery(() => getOrganizeSuggestionsOptions());

	const list = new PagedList(
		async (cursor) =>
			(await getOrganizeInbox({ query: { pageSize: 150, cursor: cursor ?? undefined } })).data
	);
	list.start();

	let busy = $state(false);
	/** Photos waiting for a destination folder. */
	let moving = $state<{ ids: string[]; selection?: Selection } | null>(null);

	// The page owns the selection: a batch opens with its photos chosen, and
	// the choice survives switching between the batch and the whole inbox.
	const selection = new Selection();
	let view = $state<CollectionView>();

	/**
	 * A proposed batch opened to check it: its photos alone, all chosen, or
	 * the whole inbox with them still chosen to add the ones it missed.
	 */
	let batch = $state<{ key: string; title: string; list: PagedList } | null>(null);
	let scope = $state<'batch' | 'all'>('batch');

	async function openBatch(suggestion: OrganizeSuggestionResponse) {
		const assetIds = suggestion.assetIds;
		const batchList = new PagedList(async () => {
			const { data } = await getOrganizeInboxItems({ body: { assetIds } });
			return data && { items: data.items, hasMore: false };
		});
		selection.clear();
		scope = 'batch';
		batch = { key: suggestion.key, title: suggestionTitle(suggestion), list: batchList };
		await batchList.start();
		// Only what is still pending: a batch can be older than the last move.
		selection.set(batchList.store.order, true);
	}

	function closeBatch() {
		batch = null;
		scope = 'batch';
		selection.clear();
	}

	const nextFrame = () => new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

	/** The whole inbox, scrolled to the batch's newest photo still chosen. */
	async function showAll() {
		const current = batch;
		if (!current) return;
		scope = 'all';
		const first = current.list.store.order.find((id) => selection.has(id));
		if (!first) return;
		// Its page may not be loaded yet; a few more pages, not the whole inbox.
		for (let i = 0; i < 20 && !list.store.byId[first] && list.hasMore; i++) await list.more();
		await tick();
		await nextFrame();
		if (batch === current && scope === 'all') await view?.reveal(first);
	}

	/** Takes photos out of the inbox, and out of the batch; an emptied batch closes. */
	function removed(ids: readonly string[]) {
		list.store.remove(ids);
		batch?.list.store.remove(ids);
		selection.set(ids, false);
		if (batch && batch.list.store.items.length === 0) closeBatch();
	}

	async function run(action: () => Promise<void>) {
		if (busy) return;
		busy = true;
		try {
			await action();
		} catch {
			toasts.error(m.organize_failed());
		} finally {
			busy = false;
		}
	}

	function move(folder: { id: string; name: string }, byYear: boolean) {
		const target = moving;
		moving = null;
		if (!target) return;
		return run(async () => {
			const result = await moveToFolder(target.ids, folder, byYear);
			removed(target.ids);
			refreshOrganize(queryClient);
			toasts.show(movedMessage(result, folder.name));
		});
	}

	function setAside(ids: string[]) {
		return run(async () => {
			await setExcluded(ids, true);
			removed(ids);
			refreshOrganize(queryClient);
			toasts.show(m.organize_set_aside({ count: ids.length }), {
				action: {
					label: m.organize_undo(),
					run: async () => {
						try {
							await setExcluded(ids, false);
							refreshOrganize(queryClient);
							list.start();
							batch?.list.start();
						} catch {
							toasts.error(m.organize_failed());
						}
					}
				}
			});
		});
	}
</script>

<svelte:head>
	<title>{m.nav_organize()} · {m.app_name()}</title>
</svelte:head>

{#key batch ? `${batch.key}:${scope}` : 'inbox'}
	{@const shown = batch && scope === 'batch' ? batch.list : list}
	<CollectionView
		store={shown.store}
		title={batch?.title ?? m.nav_organize()}
		count={batch ? m.organize_batch_selected({ count: selection.size }) : null}
		status={shown.status}
		emptyText={batch && scope === 'batch' ? m.organize_batch_empty() : m.organize_inbox_empty()}
		emptyIcon="inbox"
		onnearend={shown === list ? () => list.more() : undefined}
		{selection}
		bind:this={view}
	>
		{#snippet header()}
			{#if batch}
				<div class="batch-bar">
					<div class="batch-name">
						<button type="button" class="btn sm ghost" onclick={closeBatch}>
							<Icon name="chevronLeft" size={18} />{m.organize_batch_back()}
						</button>
						<!-- The page title hides under the selection bar: the batch keeps its name here. -->
						<strong>{batch.title}</strong>
					</div>
					<div class="segmented" role="radiogroup" aria-label={m.organize_batch_scope()}>
						<label>
							<input
								type="radio"
								name="organize-batch-scope"
								checked={scope === 'batch'}
								onchange={() => (scope = 'batch')}
							/>
							{m.organize_batch_only()}
						</label>
						<label>
							<input
								type="radio"
								name="organize-batch-scope"
								checked={scope === 'all'}
								onchange={showAll}
							/>
							{m.organize_batch_all()}
						</label>
					</div>
				</div>
			{:else}
				<OrganizeNav />
				{#if suggestions.data?.length}
					<Suggestions
						suggestions={suggestions.data}
						{busy}
						onopen={openBatch}
						ondismiss={(suggestion) => setAside(suggestion.assetIds)}
					/>
				{/if}
			{/if}
		{/snippet}
		{#snippet selectionActions(selection, batchActions)}
			<button
				type="button"
				class="icon-btn"
				disabled={busy}
				title={m.organize_move()}
				aria-label={m.organize_move()}
				onclick={() => (moving = { ids: [...selection.ids], selection })}
			>
				<Icon name="folder" />
			</button>
			<button
				type="button"
				class="icon-btn"
				disabled={busy}
				title={m.organize_set_aside_action()}
				aria-label={m.organize_set_aside_action()}
				onclick={() => setAside([...selection.ids])}
			>
				<Icon path={hidePath} />
			</button>
			<BatchActionBar
				actions={batchActions}
				{selection}
				available={['favorite', 'album', 'share', 'download', 'archive', 'trash']}
			/>
		{/snippet}
	</CollectionView>
{/key}

<FolderPickerDialog open={moving !== null} onclose={() => (moving = null)} onpick={move} />

<style>
	.batch-bar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2) var(--space-3);
		padding: var(--space-2) var(--page-gutter) var(--space-3);
	}

	.batch-name {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
	}

	.batch-name strong {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
</style>
