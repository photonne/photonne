<script lang="ts">
	import { onDestroy } from 'svelte';
	import type { SharedAssetDto } from '#lib/api/index.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import DropZone from '../upload/DropZone.svelte';
	import UploadList from '../upload/UploadList.svelte';
	import { UploadQueue } from '../upload/upload-queue.svelte.js';
	import { shareTransport } from './share-transport.js';

	interface Props {
		token: string;
		password: string | null;
		/** Each photo the album now shows, as it arrives, to add it to the grid. */
		onuploaded: (asset: SharedAssetDto) => void;
	}

	let { token, password, onuploaded }: Props = $props();

	let name = $state('');

	// One file at a time: the server rate-limits guest uploads.
	const queue = new UploadQueue(
		shareTransport(
			() => ({ token, password, name }),
			(asset) => onuploaded(asset)
		),
		{ concurrency: 1 }
	);

	const counts = $derived(queue.counts);

	onDestroy(() => queue.cancelAll());

	function add(files: File[]) {
		const { ignored } = queue.add(files);
		if (ignored > 0) toasts.show(m.upload_ignored({ count: ignored }));
	}
</script>

<section class="card" aria-labelledby="guest-upload-title">
	<header>
		<h2 id="guest-upload-title">{m.share_upload_title()}</h2>
		<p>{m.share_upload_lead()}</p>
	</header>

	<div class="row">
		<label class="field name">
			<span>{m.share_upload_name()}</span>
			<input autocomplete="name" maxlength="80" bind:value={name} />
			<small class="hint">{m.share_upload_name_hint()}</small>
		</label>

		<DropZone
			compact
			onfiles={add}
			folders={false}
			title={m.share_upload_drop()}
			hint={m.share_upload_drop_hint()}
		/>
	</div>

	{#if !queue.active && counts.done > 0}
		<div class="thanks">
			<p role="status">{m.share_upload_thanks({ count: counts.done })}</p>
		</div>
	{/if}
	{#if counts.failed > 0 && !queue.active}
		<div class="failed" role="alert">
			<span>{m.upload_count_failed({ count: counts.failed })}</span>
			<button type="button" class="btn sm" onclick={() => queue.retryFailed()}
				>{m.upload_retry_failed()}</button
			>
		</div>
	{/if}
	{#if counts.total > 0}
		<UploadList {queue} label={m.upload_queue_title()} duplicateText={m.share_upload_duplicate()} />
	{/if}
</section>

<style>
	.card {
		container-type: inline-size;
		display: grid;
		gap: var(--space-4);
		padding: var(--space-4) var(--space-6) var(--space-6);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	header p {
		margin: var(--space-1) 0 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	/* The name beside the drop zone: one row on a wide window. */
	.row {
		display: grid;
		grid-template-columns: minmax(14rem, 18rem) minmax(0, 1fr);
		align-items: start;
		gap: var(--space-4);
	}

	.hint {
		font-size: var(--font-size-xs);
	}

	@container (max-width: 820px) {
		.row {
			grid-template-columns: minmax(0, 1fr);
		}

		.name {
			max-width: 360px;
		}
	}

	.thanks {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: color-mix(in srgb, var(--color-success) 12%, transparent);
		color: var(--color-text);
		font-weight: 600;
	}

	.thanks p {
		margin: 0;
	}

	.failed {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		color: var(--color-danger);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}
</style>
