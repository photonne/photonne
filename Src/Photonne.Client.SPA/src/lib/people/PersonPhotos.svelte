<script lang="ts">
	import { untrack } from 'svelte';
	import { useQueryClient } from '@tanstack/svelte-query';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import {
		postApiPeopleByPersonIdAssetsByAssetIdUnlink,
		searchAssets,
		type PersonDto
	} from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import { PagedList } from '#lib/timeline/paged-list.svelte.js';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import { eachUntilError } from './actions.js';
	import { invalidatePeople } from './cache.js';
	import { icons } from './icons.js';
	import { displayName } from './people.js';
	import PersonTabs from './PersonTabs.svelte';
	import PersonToolbar from './PersonToolbar.svelte';

	let { person }: { person: PersonDto } = $props();

	const PAGE_SIZE = 200;
	const queryClient = useQueryClient();
	const name = $derived(displayName(person, m.people_unnamed()));

	// The search endpoint with a person filter answers full timeline items
	// (shape, favourite, Live Photo), which the photo grid needs; the offset
	// travels as the PagedList cursor.
	const personId = untrack(() => person.id);
	const list = new PagedList(async (cursor) => {
		const offset = cursor ? Number(cursor) : 0;
		const { data } = await searchAssets({
			query: { personId: [personId], pageSize: PAGE_SIZE, offset }
		});
		return data && { ...data, nextCursor: String(offset + data.items.length) };
	});
	list.start();

	let unlinking = $state<Selection | null>(null);
	let busy = $state(false);

	async function unlink() {
		const selection = unlinking;
		if (!selection || busy) return;
		busy = true;
		const ids = [...selection.ids];
		const done = await eachUntilError(ids, (assetId) =>
			postApiPeopleByPersonIdAssetsByAssetIdUnlink({ path: { personId: person.id, assetId } })
		);
		busy = false;
		unlinking = null;
		const removed = ids.slice(0, done);
		list.store.remove(removed);
		selection.set(removed, false);
		if (done < ids.length) toasts.error(m.action_failed());
		if (done > 0) toasts.show(m.people_unlinked({ count: done, name }));
		await invalidatePeople(queryClient);
	}
</script>

<CollectionView
	store={list.store}
	title={name}
	status={list.status}
	emptyText={m.people_photos_empty()}
	onnearend={() => list.more()}
>
	{#snippet toolbar()}
		<PersonToolbar {person} />
	{/snippet}
	{#snippet header()}
		<PersonTabs {person} current="photos" />
	{/snippet}
	{#snippet selectionActions(selection, batch)}
		<button
			type="button"
			class="action"
			title={m.people_unlink()}
			aria-label={m.people_unlink()}
			disabled={batch.busy || busy}
			onclick={() => (unlinking = selection)}
		>
			<Icon path={icons.personRemove} />
		</button>
		<BatchActionBar actions={batch} {selection} />
	{/snippet}
</CollectionView>

<Dialog
	open={unlinking !== null}
	title={m.people_unlink_title({ name })}
	onclose={() => (unlinking = null)}
>
	<p>{m.people_unlink_body({ count: unlinking?.size ?? 0, name })}</p>
	{#snippet actions()}
		<button type="button" onclick={() => (unlinking = null)}>{m.people_cancel()}</button>
		<button type="button" class="danger" disabled={busy} onclick={unlink}>
			{m.people_unlink()}
		</button>
	{/snippet}
</Dialog>

<style>
	p {
		margin: 0;
	}

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
