<script lang="ts">
	import { onMount } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import {
		getRemovedAlbumAssets,
		restoreAlbumAssets,
		type RemovedAlbumAssetResponse
	} from '#lib/api/index.js';
	import { relativeTime } from '#lib/admin/format.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { groupRemovals } from './removed.js';

	interface Props {
		albumId: string;
		onclose: () => void;
		/** Some came back: the album reloads its photos. */
		onrestored: () => void;
	}

	let { albumId, onclose, onrestored }: Props = $props();

	let items = $state<RemovedAlbumAssetResponse[] | null>(null);
	let failed = $state(false);
	let busy = $state(false);
	const selected = new SvelteSet<string>();

	const groups = $derived(items ? groupRemovals(items) : []);

	async function load() {
		failed = false;
		const { data, error } = await getRemovedAlbumAssets({ path: { albumId } });
		if (error || !data) failed = true;
		else items = data;
	}

	onMount(load);

	function toggle(id: string) {
		if (!selected.delete(id)) selected.add(id);
	}

	async function restore(ids: readonly string[]) {
		if (!ids.length || busy) return;
		busy = true;
		const { data, error } = await restoreAlbumAssets({
			path: { albumId },
			body: { assetIds: [...ids] }
		});
		busy = false;
		if (error || !data) {
			toasts.error(m.action_failed());
			return;
		}
		const back = new Set(data.restored);
		items = items?.filter((item) => !back.has(item.asset.id)) ?? null;
		for (const id of back) selected.delete(id);
		if (back.size) {
			toasts.show(m.albums_restored({ count: back.size }));
			onrestored();
		}
	}
</script>

<Dialog open title={m.albums_removed_recent()} {onclose} width="720px">
	<p class="hint">{m.albums_removed_hint()}</p>
	{#if failed}
		<p role="alert" class="status">{m.action_failed()}</p>
		<button type="button" class="btn" onclick={load}>{m.albums_retry()}</button>
	{:else if items === null}
		<p class="status" aria-busy="true">…</p>
	{:else if groups.length === 0}
		<p class="status">{m.albums_removed_empty()}</p>
	{:else}
		{#each groups as group (group.key)}
			<section class="group">
				<header>
					<h3 title={new Date(group.removedAt).toLocaleString()}>
						{group.removedBy
							? m.albums_removed_by({
									when: relativeTime(group.removedAt),
									name: group.removedBy
								})
							: relativeTime(group.removedAt)}
						<span class="count">· {m.albums_items({ count: group.items.length })}</span>
					</h3>
					<button
						type="button"
						class="btn sm"
						disabled={busy}
						onclick={() => restore(group.items.map((item) => item.asset.id))}
					>
						{m.albums_restore()}
					</button>
				</header>
				<ul class="thumbs">
					{#each group.items as item (item.asset.id)}
						<li>
							<button
								type="button"
								class="thumb"
								aria-pressed={selected.has(item.asset.id)}
								aria-label={item.asset.fileName}
								title={item.asset.fileName}
								onclick={() => toggle(item.asset.id)}
							>
								{#if item.asset.hasThumbnails}
									<img
										src={thumbnailUrl(item.asset.id, 'Small', item.asset.thumbnailsGeneratedAt)}
										alt=""
										loading="lazy"
									/>
								{/if}
								{#if selected.has(item.asset.id)}
									<span class="check"><Icon name="check" size={16} /></span>
								{/if}
							</button>
						</li>
					{/each}
				</ul>
			</section>
		{/each}
	{/if}

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.albums_close()}</button>
		<button
			type="button"
			class="primary"
			disabled={busy || selected.size === 0}
			onclick={() => restore([...selected])}
		>
			{m.albums_restore_selected({ count: selected.size })}
		</button>
	{/snippet}
</Dialog>

<style>
	.hint,
	.status {
		margin: 0 0 var(--space-3);
		color: var(--color-text-muted);
	}

	.group + .group {
		margin-top: var(--space-4);
	}

	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
		margin-bottom: var(--space-2);
	}

	h3 {
		margin: 0;
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.count {
		font-weight: 400;
		color: var(--color-text-muted);
	}

	.thumbs {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
		gap: var(--space-1);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.thumb {
		position: relative;
		display: block;
		width: 100%;
		aspect-ratio: 1;
		padding: 0;
		overflow: hidden;
		border: 2px solid transparent;
		border-radius: var(--radius-md);
		background: var(--color-surface);
		cursor: pointer;
	}

	.thumb[aria-pressed='true'] {
		border-color: var(--color-primary-fill);
	}

	.thumb img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.check {
		position: absolute;
		top: var(--space-1);
		right: var(--space-1);
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		background: var(--color-primary-fill);
		color: var(--color-on-primary-fill);
	}
</style>
