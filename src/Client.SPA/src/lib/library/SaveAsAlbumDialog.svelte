<script lang="ts">
	import { goto } from '$app/navigation';
	import { useQueryClient } from '@tanstack/svelte-query';
	import { addAssetsToAlbumBatch, createAlbum } from '#lib/api/index.js';
	import { getAllAlbumsQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	/**
	 * Keeps a memory (a trip, a year's favourites…) as an ordinary album: the
	 * memory is regenerated every night and may change or go; the album stays,
	 * and can be edited and shared. Mounted only while open.
	 */
	interface Props {
		/** The memory's title, as the album's name to start from. */
		name: string;
		assetIds: readonly string[];
		onclose: () => void;
	}

	let { name: initial, assetIds, onclose }: Props = $props();

	const queryClient = useQueryClient();
	// Starts from the memory's title; typing overrides it.
	let name = $derived(initial);
	let saving = $state(false);

	async function save(event: SubmitEvent) {
		event.preventDefault();
		const wanted = name.trim();
		if (!wanted || saving) return;
		saving = true;
		try {
			const { data: album } = await createAlbum({ body: { name: wanted } });
			if (!album) throw new Error('create');
			const { error } = await addAssetsToAlbumBatch({
				path: { albumId: album.id },
				body: { assetIds: [...assetIds] }
			});
			// The album exists either way: say so, and go there to fix it.
			queryClient.invalidateQueries({ queryKey: getAllAlbumsQueryKey() });
			if (error) toasts.error(m.action_failed());
			else
				toasts.show(m.memories_saved_as_album({ name: album.name }), {
					action: {
						label: m.memories_open_album(),
						run: () => goto(appHref(`/albums/${album.id}`))
					}
				});
			onclose();
		} catch {
			toasts.error(m.action_failed());
		} finally {
			saving = false;
		}
	}
</script>

<Dialog open title={m.memories_save_as_album()} onclose={() => !saving && onclose()}>
	<form id="memory-album" onsubmit={save}>
		<label class="field">
			<span>{m.memories_album_name()}</span>
			<!-- svelte-ignore a11y_autofocus -->
			<input bind:value={name} maxlength="200" autocomplete="off" autofocus />
			<span class="hint">{m.memories_save_as_album_hint({ count: assetIds.length })}</span>
		</label>
	</form>
	{#snippet actions()}
		<button type="button" disabled={saving} onclick={onclose}>{m.dialog_cancel()}</button>
		<button
			type="submit"
			form="memory-album"
			class="primary"
			disabled={saving || !name.trim() || assetIds.length === 0}
		>
			{m.memories_save()}
		</button>
	{/snippet}
</Dialog>
