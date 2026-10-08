<script lang="ts">
	import { untrack } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import BatchActionBar from '#lib/actions/BatchActionBar.svelte';
	import type { BatchActions } from '#lib/actions/batch-actions.svelte.js';
	import {
		addAssetsToAlbumBatch,
		deleteAlbum,
		getAlbumAssets,
		leaveAlbum,
		removeAssetFromAlbum,
		setAlbumCover
	} from '#lib/api/index.js';
	import { getAlbumByIdOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import CollectionView from '#lib/timeline/CollectionView.svelte';
	import type { Selection } from '#lib/timeline/selection.svelte.js';
	import { AlbumList } from './album-list-store.svelte.js';
	import { isSmart } from './album-list.js';
	import AlbumFormDialog from './AlbumFormDialog.svelte';
	import { invalidateAlbums, toggleAlbumPin } from './cache.js';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { icons } from './icons.js';
	import { orderItems, type ItemOrder } from './item-order.js';
	import MenuButton, { type MenuItem } from './MenuButton.svelte';
	import ShareDialog from './ShareDialog.svelte';

	let { albumId }: { albumId: string } = $props();

	const queryClient = useQueryClient();
	const albumQuery = createQuery(() => getAlbumByIdOptions({ path: { albumId } }));
	const album = $derived(albumQuery.data);
	const smart = $derived(album ? isSmart(album) : false);

	// A smart album has no manual order: the server returns it newest first.
	let order = $state<ItemOrder>('album');
	/** Ids in the album's own order, to come back to it after a date sort. */
	let albumOrder: string[] = [];

	const list = new AlbumList(async () => {
		const { data } = await getAlbumAssets({ path: { albumId: untrack(() => albumId) } });
		if (!data) return undefined;
		albumOrder = data.map((item) => item.id);
		return orderItems(
			data,
			untrack(() => order),
			(item) => item.fileCreatedAt
		);
	});
	list.start();

	function reorder(next: ItemOrder) {
		order = next;
		const index = new Map(albumOrder.map((id, i) => [id, i]));
		const inAlbumOrder = [...list.store.items].sort(
			(a, b) => (index.get(a.id) ?? 0) - (index.get(b.id) ?? 0)
		);
		list.store.items = orderItems(inAlbumOrder, next, (item) => item.capturedAt);
	}

	const canWrite = $derived(album?.canWrite ?? false);
	const canRemove = $derived(canWrite && !smart);
	const ownsPhotos = $derived(album?.isOwner ?? false);

	let editing = $state(false);
	let sharing = $state<'people' | 'links' | null>(null);
	let confirming = $state<'delete' | 'leave' | null>(null);
	let busy = $state(false);

	const menu = $derived.by(() => {
		if (!album) return [];
		const items: MenuItem[] = [];
		if (canWrite) items.push({ label: m.albums_edit(), icon: 'edit', run: () => (editing = true) });
		if (album.canManagePermissions)
			items.push({
				label: m.albums_share_people(),
				icon: { path: icons.personAdd },
				run: () => (sharing = 'people')
			});
		if (canWrite)
			items.push({ label: m.albums_share_links(), icon: 'link', run: () => (sharing = 'links') });
		if (album.isOwner || album.canDelete)
			items.push({
				label: m.albums_delete(),
				icon: 'delete',
				danger: true,
				run: () => (confirming = 'delete')
			});
		if (!album.isOwner)
			items.push({
				label: m.albums_leave(),
				icon: { path: icons.leave },
				danger: true,
				run: () => (confirming = 'leave')
			});
		return items;
	});

	async function removeFromAlbum(selection: Selection) {
		const ids = [...selection.ids];
		if (!ids.length || busy) return;
		busy = true;
		// No batch endpoint for removal: one request per photo.
		const results = await Promise.all(
			ids.map((assetId) => removeAssetFromAlbum({ path: { albumId, assetId } }))
		);
		busy = false;
		const removed = ids.filter((_, i) => !results[i].error);
		if (removed.length < ids.length) toasts.error(m.action_failed());
		if (!removed.length) return;
		list.store.remove(removed);
		selection.set(removed, false);
		invalidateAlbums(queryClient, albumId);
		toasts.show(m.albums_removed({ count: removed.length }), {
			action: {
				label: m.action_undo(),
				run: async () => {
					const { error } = await addAssetsToAlbumBatch({
						path: { albumId },
						body: { assetIds: removed }
					});
					if (error) toasts.error(m.action_failed());
					else {
						invalidateAlbums(queryClient, albumId);
						list.store.reload();
					}
				}
			}
		});
	}

	async function setCover(assetId: string | undefined, selection?: Selection) {
		if (!assetId) return;
		const { error } = await setAlbumCover({ path: { albumId }, body: { assetId } });
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		selection?.clear();
		invalidateAlbums(queryClient, albumId);
		toasts.show(m.albums_cover_set());
	}

	async function confirm() {
		const action = confirming;
		if (!album || !action) return;
		busy = true;
		const { error } =
			action === 'delete'
				? await deleteAlbum({ path: { albumId } })
				: await leaveAlbum({ path: { albumId } });
		busy = false;
		confirming = null;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		invalidateAlbums(queryClient);
		toasts.show(
			action === 'delete'
				? m.albums_deleted({ name: album.name })
				: m.albums_left({ name: album.name })
		);
		await goto(appHref('/albums'), { replace: true });
	}

	function saved() {
		editing = false;
		invalidateAlbums(queryClient, albumId);
		// A new rule means new contents.
		if (smart) list.store.reload();
		toasts.show(m.albums_saved());
	}

	const available = $derived(
		ownsPhotos
			? (['favorite', 'album', 'share', 'download', 'trash'] as const)
			: (['album', 'share', 'download'] as const)
	);
</script>

<svelte:head>
	<title>{album?.name ?? m.nav_albums()} · {m.app_name()}</title>
</svelte:head>

{#if albumQuery.isError}
	<div class="missing">
		<p role="alert">{m.albums_not_found()}</p>
		<a href={appHref('/albums')}>{m.albums_back()}</a>
	</div>
{:else}
	<CollectionView
		store={list.store}
		title={album?.name ?? ''}
		status={albumQuery.isPending ? 'pending' : list.status}
		emptyText={smart ? m.albums_empty_smart() : m.albums_empty_album()}
		headers={false}
		viewerActions={ownsPhotos ? ['trash', 'album'] : ['album']}
	>
		{#snippet toolbar()}
			{#if album}
				<label class="order">
					<span class="visually-hidden">{m.albums_order()}</span>
					<select value={order} onchange={(e) => reorder(e.currentTarget.value as ItemOrder)}>
						{#if !smart}<option value="album">{m.albums_order_album()}</option>{/if}
						<option value={smart ? 'album' : 'newest'}>{m.albums_order_newest()}</option>
						<option value="oldest">{m.albums_order_oldest()}</option>
					</select>
				</label>
				{#if album.canManagePermissions || canWrite}
					<button
						type="button"
						class="tool"
						title={m.albums_share()}
						onclick={() => (sharing = album.canManagePermissions ? 'people' : 'links')}
					>
						<Icon name="share" size={18} /><span class="label">{m.albums_share()}</span>
					</button>
				{/if}
				<button
					type="button"
					class="tool icon"
					aria-pressed={album.isPinned}
					aria-label={album.isPinned ? m.albums_unpin() : m.albums_pin()}
					title={album.isPinned ? m.albums_unpin() : m.albums_pin()}
					onclick={() => toggleAlbumPin(queryClient, album)}
				>
					<Icon name="pin" size={18} />
				</button>
				<MenuButton label={m.albums_more()} items={menu} />
			{/if}
		{/snippet}

		{#snippet header()}
			{#if album}
				<div class="about">
					{#if album.description}<p class="description">{album.description}</p>{/if}
					<p class="meta">
						<span>{m.albums_items({ count: album.assetCount })}</span>
						{#if smart}
							<span class="badge" title={m.albums_smart_hint()}>
								<Icon path={icons.smart} size={14} />{m.albums_smart()}
							</span>
						{/if}
						{#if !album.isOwner}
							<span class="badge">{m.albums_shared_with_you()}</span>
						{:else if album.isShared}
							<span class="badge"><Icon name="people" size={14} />{m.albums_shared()}</span>
						{/if}
						{#if album.hasActiveShareLink}
							<span class="badge"><Icon name="link" size={14} />{m.albums_link_active()}</span>
						{/if}
					</p>
				</div>
			{/if}
		{/snippet}

		{#snippet viewerExtra(assetId: string)}
			{#if canWrite}
				<button
					type="button"
					class="viewer-action"
					title={m.albums_set_cover()}
					aria-label={m.albums_set_cover()}
					onclick={() => setCover(assetId)}
				>
					<Icon path={icons.cover} />
				</button>
			{/if}
		{/snippet}

		{#snippet selectionActions(selection: Selection, batch: BatchActions)}
			{#if canRemove}
				<button
					type="button"
					class="action"
					title={m.albums_remove()}
					aria-label={m.albums_remove()}
					disabled={busy}
					onclick={() => removeFromAlbum(selection)}
				>
					<Icon path={icons.remove} />
				</button>
			{/if}
			{#if canWrite && selection.size === 1}
				<button
					type="button"
					class="action"
					title={m.albums_set_cover()}
					aria-label={m.albums_set_cover()}
					onclick={() => setCover([...selection.ids][0], selection)}
				>
					<Icon path={icons.cover} />
				</button>
			{/if}
			<BatchActionBar actions={batch} {selection} {available} />
		{/snippet}
	</CollectionView>
{/if}

{#if editing && album}
	<AlbumFormDialog {album} onclose={() => (editing = false)} onsaved={saved} />
{/if}

{#if sharing && album}
	<ShareDialog
		kind="album"
		targetId={albumId}
		name={album.name}
		canManagePeople={album.canManagePermissions}
		canLink={canWrite}
		initialTab={sharing}
		onclose={() => (sharing = null)}
		onchanged={() => invalidateAlbums(queryClient, albumId)}
	/>
{/if}

<ConfirmDialog
	danger
	open={confirming !== null}
	title={confirming === 'leave' ? m.albums_leave_title() : m.albums_delete_title()}
	message={confirming === 'leave'
		? m.albums_leave_message({ name: album?.name ?? '' })
		: m.albums_delete_message({ name: album?.name ?? '' })}
	confirmLabel={confirming === 'leave' ? m.albums_leave() : m.albums_delete()}
	{busy}
	onconfirm={confirm}
	onclose={() => (confirming = null)}
/>

<style>
	.order select {
		height: 36px;
		padding: 0 var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.tool {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		height: 36px;
		padding: 0 var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		cursor: pointer;
	}

	.tool.icon {
		width: 36px;
		padding: 0;
		justify-content: center;
		border-color: transparent;
		border-radius: 50%;
	}

	/* Narrow windows keep the icon; the text stays as the accessible name. */
	@media (max-width: 1180px) {
		.tool .label {
			position: absolute;
			width: 1px;
			height: 1px;
			overflow: hidden;
			clip: rect(0 0 0 0);
			white-space: nowrap;
		}

		.tool:has(.label) {
			width: 36px;
			padding: 0;
			justify-content: center;
		}
	}

	.tool:hover {
		background: var(--color-surface);
	}

	.tool[aria-pressed='true'] {
		color: var(--color-accent);
	}

	.about {
		padding: var(--space-1) var(--space-4) var(--space-2);
	}

	.description {
		margin: 0 0 var(--space-1);
		max-width: 80ch;
		white-space: pre-line;
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
		margin: 0;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.badge {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 1px var(--space-2);
		border-radius: 999px;
		background: var(--color-surface);
		font-size: var(--font-size-xs);
		font-weight: 600;
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

	.viewer-action:hover {
		background: rgb(255 255 255 / 0.12);
	}

	.action:hover {
		background: var(--color-surface);
	}

	.action:disabled {
		opacity: 0.4;
		cursor: progress;
	}

	.missing {
		padding: var(--space-6);
		display: grid;
		gap: var(--space-2);
		justify-items: start;
	}

	.missing p {
		margin: 0;
		color: var(--color-text-muted);
	}
</style>
