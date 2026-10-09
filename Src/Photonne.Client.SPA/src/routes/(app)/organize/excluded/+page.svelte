<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import { getOrganizeExcluded } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { moveToInboxPath } from '#lib/search/icons.js';
	import OrganizeNav from '#lib/search/organize/OrganizeNav.svelte';
	import { refreshOrganize, setExcluded } from '#lib/search/organize/organize.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';

	const queryClient = useQueryClient();
	const list = new PagedList(
		async (cursor) =>
			(await getOrganizeExcluded({ query: { pageSize: 150, cursor: cursor ?? undefined } })).data
	);
	list.start();

	let busy = $state(false);

	/** Back to the inbox: they count as pending again. Undo sets them aside again. */
	async function putBack(selection: Selection) {
		if (busy) return;
		const ids = [...selection.ids];
		busy = true;
		try {
			await setExcluded(ids, false);
			list.store.remove(ids);
			selection.set(ids, false);
			refreshOrganize(queryClient);
			toasts.show(m.organize_put_back({ count: ids.length }), {
				action: {
					label: m.organize_undo(),
					run: async () => {
						try {
							await setExcluded(ids, true);
							refreshOrganize(queryClient);
							list.start();
						} catch {
							toasts.error(m.organize_failed());
						}
					}
				}
			});
		} catch {
			toasts.error(m.organize_failed());
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head>
	<title>{m.organize_tab_excluded()} · {m.nav_organize()} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	title={m.nav_organize()}
	status={list.status}
	emptyText={m.organize_excluded_empty()}
	onnearend={() => list.more()}
>
	{#snippet header()}
		<OrganizeNav />
		<p class="hint">{m.organize_excluded_hint()}</p>
	{/snippet}
	{#snippet selectionActions(selection, batch)}
		<button type="button" class="btn sm" disabled={busy} onclick={() => putBack(selection)}>
			<Icon path={moveToInboxPath} />
			{m.organize_put_back_action()}
		</button>
		<BatchActionBar
			actions={batch}
			{selection}
			available={['favorite', 'album', 'share', 'download']}
		/>
	{/snippet}
</CollectionView>

<style>
	.hint {
		max-width: 80ch;
		margin: 0;
		padding: var(--space-3) var(--page-gutter) var(--space-1);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
