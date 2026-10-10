<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { createAlbum, type AlbumResponse } from '#lib/api/index.js';
	import {
		getAllAlbumsOptions,
		getAllAlbumsQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		open: boolean;
		onclose: () => void;
		onpick: (album: { id: string; name: string }) => void;
	}

	let { open, onclose, onpick }: Props = $props();

	const queryClient = useQueryClient();
	const albums = createQuery(() => ({ ...getAllAlbumsOptions(), enabled: open }));

	let search = $state('');
	let newName = $state('');
	let creating = $state(false);

	// Only manual albums the user can add to; smart albums fill themselves.
	const writable = $derived(
		(albums.data ?? []).filter((album: AlbumResponse) => album.canWrite && album.kind !== 'Smart')
	);
	const filtered = $derived(
		search.trim()
			? writable.filter((album) => album.name.toLowerCase().includes(search.trim().toLowerCase()))
			: writable
	);

	async function create(event: SubmitEvent) {
		event.preventDefault();
		const name = newName.trim();
		if (!name) return;
		creating = true;
		const { data } = await createAlbum({ body: { name } });
		creating = false;
		if (!data) {
			toasts.error(m.action_failed());
			return;
		}
		queryClient.invalidateQueries({ queryKey: getAllAlbumsQueryKey() });
		newName = '';
		onpick({ id: data.id, name: data.name });
	}
</script>

<Dialog {open} title={m.album_picker_title()} {onclose}>
	<div class="picker">
		<input type="search" placeholder={m.album_picker_search()} bind:value={search} />

		<ul role="listbox" aria-label={m.album_picker_title()}>
			{#each filtered as album (album.id)}
				<li role="option" aria-selected="false">
					<button type="button" onclick={() => onpick({ id: album.id, name: album.name })}>
						<span class="name">{album.name}</span>
						<span class="count">{m.album_picker_items({ count: album.assetCount })}</span>
					</button>
				</li>
			{:else}
				{#if albums.isSuccess}
					<li class="empty">{m.album_picker_empty()}</li>
				{/if}
			{/each}
		</ul>

		<form onsubmit={create}>
			<input
				aria-label={m.album_picker_new_name()}
				placeholder={m.album_picker_new_name()}
				bind:value={newName}
			/>
			<button type="submit" disabled={!newName.trim() || creating}>{m.album_picker_create()}</button
			>
		</form>
	</div>
</Dialog>

<style>
	.picker {
		display: grid;
		gap: var(--space-3);
	}

	input {
		width: 100%;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		max-height: 320px;
		overflow-y: auto;
		display: grid;
		gap: 2px;
	}

	li button {
		width: 100%;
		display: flex;
		justify-content: space-between;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		text-align: left;
		cursor: pointer;
	}

	li button:hover,
	li button:focus-visible {
		background: var(--color-surface);
	}

	.count,
	.empty {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.empty {
		padding: var(--space-2) var(--space-3);
	}

	form {
		display: flex;
		gap: var(--space-2);
		padding-top: var(--space-3);
		border-top: 1px solid var(--color-border);
	}

	form button {
		flex: none;
		padding: var(--space-2) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
		cursor: pointer;
	}

	form button:disabled {
		opacity: 0.5;
		cursor: default;
	}
</style>
