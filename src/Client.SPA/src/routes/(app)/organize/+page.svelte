<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import FolderPickerDialog from '#lib/actions/FolderPickerDialog.svelte';
	import {
		assetYearBreakdown,
		getOrganizeInbox,
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
	import ReviewMoveDialog from '#lib/search/organize/ReviewMoveDialog.svelte';
	import { reviewGroups, type ReviewGroup } from '#lib/search/organize/review.js';
	import { suggestionTitle } from '#lib/search/organize/suggestion.js';
	import Suggestions from '#lib/search/organize/Suggestions.svelte';
	import { hidePath } from '#lib/search/icons.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';

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
	let reviewing = $state<{ title: string; groups: ReviewGroup[] } | null>(null);

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
			list.store.remove(target.ids);
			target.selection?.set(target.ids, false);
			refreshOrganize(queryClient);
			toasts.show(movedMessage(result, folder.name));
		});
	}

	function setAside(ids: string[], selection?: Selection) {
		return run(async () => {
			await setExcluded(ids, true);
			list.store.remove(ids);
			selection?.set(ids, false);
			refreshOrganize(queryClient);
			toasts.show(m.organize_set_aside({ count: ids.length }), {
				action: {
					label: m.organize_undo(),
					run: async () => {
						try {
							await setExcluded(ids, false);
							refreshOrganize(queryClient);
							list.start();
						} catch {
							toasts.error(m.organize_failed());
						}
					}
				}
			});
		});
	}

	function review(suggestion: OrganizeSuggestionResponse) {
		return run(async () => {
			const title = suggestionTitle(suggestion);
			// Grouped by capture year, as a move "by year" would file them.
			const { data } = await assetYearBreakdown({ body: { assetIds: suggestion.assetIds } });
			const groups = data ? reviewGroups(data.groups) : [];
			reviewing = {
				title,
				groups: groups.length ? groups : [{ label: title, ids: suggestion.assetIds }]
			};
		});
	}
</script>

<svelte:head>
	<title>{m.nav_organize()} · {m.app_name()}</title>
</svelte:head>

<CollectionView
	store={list.store}
	title={m.nav_organize()}
	status={list.status}
	emptyText={m.organize_inbox_empty()}
	onnearend={() => list.more()}
>
	{#snippet header()}
		<OrganizeNav />
		{#if suggestions.data?.length}
			<Suggestions
				suggestions={suggestions.data}
				{busy}
				onreview={review}
				ondismiss={(suggestion) => setAside(suggestion.assetIds)}
			/>
		{/if}
	{/snippet}
	{#snippet selectionActions(selection, batch)}
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
			onclick={() => setAside([...selection.ids], selection)}
		>
			<Icon path={hidePath} />
		</button>
		<BatchActionBar
			actions={batch}
			{selection}
			available={['favorite', 'album', 'share', 'download', 'archive', 'trash']}
		/>
	{/snippet}
</CollectionView>

<ReviewMoveDialog
	open={reviewing !== null}
	title={reviewing?.title ?? ''}
	groups={reviewing?.groups ?? []}
	confirmLabel={m.organize_choose_destination()}
	onclose={() => (reviewing = null)}
	onconfirm={(kept) => {
		reviewing = null;
		moving = { ids: kept };
	}}
/>

<FolderPickerDialog open={moving !== null} onclose={() => (moving = null)} onpick={move} />
