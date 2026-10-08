<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import AlbumPickerDialog from '#lib/actions/AlbumPickerDialog.svelte';
	import DropZone from '#lib/account/upload/DropZone.svelte';
	import UploadList from '#lib/account/upload/UploadList.svelte';
	import { uploads } from '#lib/account/upload/uploads.svelte.js';
	import { addAssetsToAlbumBatch } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const queryClient = useQueryClient();
	let pickingAlbum = $state(false);

	const counts = $derived(uploads.counts);
	const finished = $derived(counts.total - counts.pending);

	// New photos change the timeline, months, folders, storage…: everything
	// cached is stale once a batch lands.
	$effect(() =>
		uploads.ondrained((assetIds) => {
			queryClient.invalidateQueries();
			toasts.show(m.upload_batch_done({ count: assetIds.length }));
		})
	);

	function add(files: File[]) {
		const { ignored } = uploads.add(files);
		if (ignored > 0) toasts.show(m.upload_ignored({ count: ignored }));
	}

	async function addBatchToAlbum(album: { id: string; name: string }) {
		pickingAlbum = false;
		const assetIds = uploads.lastBatch;
		const { error } = await addAssetsToAlbumBatch({
			path: { albumId: album.id },
			body: { assetIds }
		});
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		queryClient.invalidateQueries();
		toasts.show(m.action_added_to_album({ count: assetIds.length, album: album.name }));
	}
</script>

<svelte:head>
	<title>{m.upload_title()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<header class="head">
		<h1>{m.upload_title()}</h1>
		<p>{m.upload_subtitle()}</p>
	</header>

	<DropZone onfiles={add} />

	{#if uploads.lastBatch.length > 0 && !uploads.active}
		<div class="batch" role="status">
			<Icon name="check" />
			<span>{m.upload_batch_done({ count: uploads.lastBatch.length })}</span>
			<a href={appHref('/')}>{m.upload_view_photos()}</a>
			<button type="button" onclick={() => (pickingAlbum = true)}>
				<Icon name="albumAdd" size={18} />
				{m.upload_add_to_album()}
			</button>
		</div>
	{/if}

	{#if counts.total > 0}
		<section class="queue" aria-labelledby="queue-title">
			<div class="queue-head">
				<div class="summary">
					<h2 id="queue-title">{m.upload_queue_title()}</h2>
					<p>
						{#if counts.pending > 0}{m.upload_count_pending({ count: counts.pending })} ·
						{/if}{m.upload_count_done({ count: counts.done })}{#if counts.duplicate > 0}
							· {m.upload_count_duplicate({ count: counts.duplicate })}{/if}{#if counts.failed > 0}
							· <strong class="failed">{m.upload_count_failed({ count: counts.failed })}</strong
							>{/if}{#if counts.skipped > 0}
							· {m.upload_count_skipped({ count: counts.skipped })}{/if}
					</p>
				</div>
				<div class="tools">
					{#if counts.failed > 0}
						<button type="button" onclick={() => uploads.retryFailed()}>
							<Icon name="refresh" size={18} />
							{m.upload_retry_failed()}
						</button>
					{/if}
					{#if uploads.active}
						<button type="button" onclick={() => uploads.cancelAll()}>
							<Icon name="close" size={18} />
							{m.upload_cancel_all()}
						</button>
					{/if}
					{#if finished > 0}
						<button type="button" onclick={() => uploads.clearFinished()}>
							{m.upload_clear_finished()}
						</button>
					{/if}
				</div>
			</div>
			{#if uploads.active}
				<progress
					class="overall"
					max="1"
					value={uploads.progress}
					aria-label={m.upload_overall_progress()}
				></progress>
			{/if}
			<UploadList queue={uploads} label={m.upload_queue_title()} />
		</section>
	{/if}
</div>

<AlbumPickerDialog
	open={pickingAlbum}
	onclose={() => (pickingAlbum = false)}
	onpick={addBatchToAlbum}
/>

<style>
	.page {
		display: grid;
		gap: var(--space-4);
		max-width: 960px;
		padding: var(--space-4) var(--space-6) var(--space-8);
	}

	.head h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.head p {
		margin: var(--space-1) 0 0;
		color: var(--color-text-muted);
	}

	.batch {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		border: 1px solid color-mix(in srgb, var(--color-accent) 40%, var(--color-border));
		border-radius: var(--radius-md);
		background: color-mix(in srgb, var(--color-accent) 8%, var(--color-surface-raised));
		color: var(--color-accent);
	}

	.batch span {
		flex: 1;
		color: var(--color-text);
		font-weight: 600;
	}

	.batch a {
		color: var(--color-accent);
		font-weight: 600;
	}

	.queue {
		display: grid;
		gap: var(--space-3);
	}

	.queue-head {
		display: flex;
		flex-wrap: wrap;
		align-items: flex-end;
		gap: var(--space-3);
	}

	.summary {
		flex: 1;
		min-width: 16rem;
	}

	.summary h2 {
		margin: 0;
		font-size: var(--font-size-md);
	}

	.summary p {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.failed {
		color: var(--color-danger);
	}

	.tools {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	button {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		cursor: pointer;
		font-size: var(--font-size-sm);
	}

	button:hover {
		background: var(--color-surface);
	}

	.overall {
		width: 100%;
		height: 6px;
		border: 0;
		border-radius: 3px;
		overflow: hidden;
		appearance: none;
		background: var(--color-surface);
	}

	.overall::-webkit-progress-bar {
		background: var(--color-surface);
	}

	.overall::-webkit-progress-value {
		background: var(--color-accent);
	}

	.overall::-moz-progress-bar {
		background: var(--color-accent);
	}
</style>
