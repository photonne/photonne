<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import AlbumPickerDialog from '#lib/actions/AlbumPickerDialog.svelte';
	import DropZone from '#lib/account/upload/DropZone.svelte';
	import UploadList from '#lib/account/upload/UploadList.svelte';
	import { uploads } from '#lib/account/upload/uploads.svelte.js';
	import { addAssetsToAlbumBatch } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
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
	<PageHeader title={m.upload_title()} subtitle={m.upload_subtitle()} />

	<div class="body">
		<div class="drop">
			<DropZone onfiles={add} />
		</div>

		{#if uploads.lastBatch.length > 0 && !uploads.active}
			<div class="batch" role="status">
				<span class="done-mark"><Icon name="check" size={20} /></span>
				<span class="done-text">{m.upload_batch_done({ count: uploads.lastBatch.length })}</span>
				<a class="btn sm ghost" href={appHref('/')}>{m.upload_view_photos()}</a>
				<button type="button" class="btn sm" onclick={() => (pickingAlbum = true)}>
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
								· {m.upload_count_duplicate({
									count: counts.duplicate
								})}{/if}{#if counts.failed > 0}
								· <strong class="failed">{m.upload_count_failed({ count: counts.failed })}</strong
								>{/if}{#if counts.skipped > 0}
								· {m.upload_count_skipped({ count: counts.skipped })}{/if}
						</p>
					</div>
					<div class="tools">
						{#if counts.failed > 0}
							<button type="button" class="btn sm" onclick={() => uploads.retryFailed()}>
								<Icon name="refresh" size={18} />
								{m.upload_retry_failed()}
							</button>
						{/if}
						{#if uploads.active}
							<button type="button" class="btn sm" onclick={() => uploads.cancelAll()}>
								<Icon name="close" size={18} />
								{m.upload_cancel_all()}
							</button>
						{/if}
						{#if finished > 0}
							<button type="button" class="btn sm ghost" onclick={() => uploads.clearFinished()}>
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
</div>

<AlbumPickerDialog
	open={pickingAlbum}
	onclose={() => (pickingAlbum = false)}
	onpick={addBatchToAlbum}
/>

<style>
	/* Full width, like the rest of the app: the drop zone is the page. */
	.page {
		padding-bottom: var(--space-8);
	}

	/* A big target for the drag: its content centred in the height. */
	.drop :global(.area) {
		min-height: clamp(16rem, 38vh, 26rem);
		align-content: center;
	}

	.body {
		display: grid;
		gap: var(--space-6);
		padding: var(--space-2) var(--page-gutter) 0;
	}

	.batch {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-2) var(--space-3);
		padding: var(--space-3) var(--space-3) var(--space-3) var(--space-4);
		border: 1px solid color-mix(in srgb, var(--color-success) 40%, var(--color-border));
		border-radius: var(--radius-lg);
		background: color-mix(in srgb, var(--color-success) 8%, var(--color-surface-raised));
	}

	.done-mark {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		border-radius: 50%;
		background: color-mix(in srgb, var(--color-success) 16%, transparent);
		color: var(--color-success);
	}

	.done-text {
		flex: 1;
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
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	.summary p {
		margin: var(--space-1) 0 0;
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

	.overall {
		width: 100%;
		height: 6px;
		border: 0;
		border-radius: 3px;
		overflow: hidden;
		appearance: none;
		background: var(--color-placeholder);
	}

	.overall::-webkit-progress-bar {
		background: var(--color-placeholder);
	}

	.overall::-webkit-progress-value {
		background: var(--color-accent);
	}

	.overall::-moz-progress-bar {
		background: var(--color-accent);
	}
</style>
