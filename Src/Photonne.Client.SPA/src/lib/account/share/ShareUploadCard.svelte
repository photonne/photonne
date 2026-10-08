<script lang="ts">
	import { onDestroy } from 'svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import DropZone from '../upload/DropZone.svelte';
	import UploadList from '../upload/UploadList.svelte';
	import { UploadQueue } from '../upload/upload-queue.svelte.js';
	import { shareTransport } from './share-transport.js';

	interface Props {
		token: string;
		password: string | null;
		/** Called after a batch with new photos, to show them in the album. */
		onuploaded: () => void;
	}

	let { token, password, onuploaded }: Props = $props();

	let name = $state('');
	let reported = 0;

	// One file at a time: the server rate-limits guest uploads.
	const queue = new UploadQueue(
		shareTransport(() => ({ token, password, name })),
		{ concurrency: 1 }
	);

	const counts = $derived(queue.counts);

	$effect(() => {
		// A batch ended: thank the guest and refresh the album once.
		if (!queue.active && counts.done > reported) {
			reported = counts.done;
			onuploaded();
		}
	});

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

	<label class="field">
		<span>{m.share_upload_name()}</span>
		<input autocomplete="name" maxlength="80" bind:value={name} />
		<small>{m.share_upload_name_hint()}</small>
	</label>

	<DropZone
		onfiles={add}
		folders={false}
		title={m.share_upload_drop()}
		hint={m.share_upload_drop_hint()}
	/>

	{#if !queue.active && counts.done > 0}
		<p class="thanks" role="status">{m.share_upload_thanks({ count: counts.done })}</p>
	{/if}
	{#if counts.failed > 0 && !queue.active}
		<div class="failed" role="alert">
			<span>{m.upload_count_failed({ count: counts.failed })}</span>
			<button type="button" onclick={() => queue.retryFailed()}>{m.upload_retry_failed()}</button>
		</div>
	{/if}
	{#if counts.total > 0}
		<UploadList {queue} label={m.upload_queue_title()} duplicateText={m.share_upload_duplicate()} />
	{/if}
</section>

<style>
	.card {
		display: grid;
		gap: var(--space-4);
		padding: var(--space-6);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-lg);
	}

	header p {
		margin: var(--space-1) 0 0;
		color: var(--color-text-muted);
	}

	.field {
		display: grid;
		gap: var(--space-1);
		max-width: 360px;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	input {
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		color: var(--color-text);
		font-size: var(--font-size-md);
	}

	.thanks {
		margin: 0;
		padding: var(--space-3) var(--space-4);
		border-radius: var(--radius-md);
		background: color-mix(in srgb, var(--color-accent) 10%, transparent);
		font-weight: 600;
	}

	.failed {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		color: var(--color-danger);
	}

	.failed button {
		padding: var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		color: var(--color-text);
		cursor: pointer;
	}
</style>
