<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { formatBytes } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { ICON_CLOUD_DONE, ICON_ERROR } from '../icons.js';
	import { failureText } from './labels.js';
	import type { UploadEntry, UploadQueue } from './upload-queue.svelte.js';

	interface Props {
		queue: UploadQueue;
		label: string;
		/** What "the server already had it" means here (library vs. shared album). */
		duplicateText?: string;
	}

	let { queue, label, duplicateText }: Props = $props();

	function statusText(entry: UploadEntry) {
		switch (entry.status) {
			case 'checking':
				return m.upload_status_checking();
			case 'queued':
				return m.upload_status_queued();
			case 'uploading':
				return m.upload_status_uploading({
					percent: Math.round((entry.loaded / Math.max(1, entry.file.size)) * 100)
				});
			case 'done':
				return m.upload_status_done();
			case 'duplicate':
				return duplicateText ?? m.upload_status_duplicate();
			case 'cancelled':
				return m.upload_status_cancelled();
			case 'skipped':
				return m.upload_status_too_large();
			case 'failed':
				return failureText(entry.failure ?? 'server');
		}
	}

	const isVideo = (entry: UploadEntry) =>
		entry.file.type.startsWith('video/') ||
		/\.(mp4|mov|m4v|avi|mkv|webm|3gp)$/i.test(entry.file.name);
</script>

<ul class="list" aria-label={label}>
	{#each queue.entries as entry (entry.id)}
		{@const name = entry.file.name}
		<li class="row {entry.status}">
			<span class="kind" aria-hidden="true">
				{#if entry.status === 'done' || entry.status === 'duplicate'}
					<Icon path={ICON_CLOUD_DONE} size={20} />
				{:else if entry.status === 'failed' || entry.status === 'skipped'}
					<Icon path={ICON_ERROR} size={20} />
				{:else}
					<Icon name={isVideo(entry) ? 'play' : 'photos'} size={20} />
				{/if}
			</span>
			<span class="name" title={name}>{name}</span>
			<span class="size">{formatBytes(entry.file.size)}</span>
			<span class="status" aria-live={entry.status === 'failed' ? 'polite' : undefined}>
				{statusText(entry)}
			</span>
			<span class="actions">
				{#if entry.status === 'failed' || entry.status === 'cancelled'}
					<button
						type="button"
						aria-label={m.upload_retry_one({ name })}
						title={m.upload_retry()}
						onclick={() => queue.retry(entry.id)}
					>
						<Icon name="refresh" size={18} />
					</button>
				{/if}
				{#if entry.status === 'checking' || entry.status === 'queued' || entry.status === 'uploading'}
					<button
						type="button"
						aria-label={m.upload_cancel_one({ name })}
						title={m.upload_cancel()}
						onclick={() => queue.cancel(entry.id)}
					>
						<Icon name="close" size={18} />
					</button>
				{:else}
					<button
						type="button"
						aria-label={m.upload_remove_one({ name })}
						title={m.upload_remove()}
						onclick={() => queue.remove(entry.id)}
					>
						<Icon name="delete" size={18} />
					</button>
				{/if}
			</span>
			{#if entry.status === 'uploading'}
				<progress
					class="bar"
					max={entry.file.size || 1}
					value={entry.loaded}
					aria-label={m.upload_progress_of({ name })}
				></progress>
			{/if}
		</li>
	{/each}
</ul>

<style>
	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
		overflow: hidden;
	}

	.row {
		position: relative;
		display: grid;
		grid-template-columns: 24px minmax(0, 1fr) auto minmax(9rem, auto) auto;
		align-items: center;
		gap: var(--space-3);
		min-height: 48px;
		padding: var(--space-1) var(--space-2) var(--space-1) var(--space-4);
		font-size: var(--font-size-sm);
		/* Long folder drops: rows off screen skip layout and paint. */
		content-visibility: auto;
		contain-intrinsic-size: auto 48px;
	}

	.row + .row {
		border-top: 1px solid var(--color-border);
	}

	.kind {
		display: grid;
		place-items: center;
		color: var(--color-text-muted);
	}

	.done .kind,
	.duplicate .kind {
		color: var(--color-accent);
	}

	.failed .kind,
	.skipped .kind,
	.failed .status,
	.skipped .status {
		color: var(--color-danger);
	}

	.name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.size,
	.status {
		color: var(--color-text-muted);
		white-space: nowrap;
	}

	.status {
		text-align: right;
		font-variant-numeric: tabular-nums;
	}

	.cancelled .name {
		color: var(--color-text-muted);
		text-decoration: line-through;
	}

	.actions {
		display: flex;
		gap: 2px;
		min-width: 72px;
		justify-content: flex-end;
	}

	.actions button {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
	}

	.actions button:hover {
		background: var(--color-surface);
		color: var(--color-text);
	}

	.bar {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		width: 100%;
		height: 3px;
		border: 0;
		appearance: none;
		background: transparent;
	}

	.bar::-webkit-progress-bar {
		background: transparent;
	}

	.bar::-webkit-progress-value {
		background: var(--color-accent);
		transition: width var(--duration-fast);
	}

	.bar::-moz-progress-bar {
		background: var(--color-accent);
	}

	@media (max-width: 720px) {
		.row {
			grid-template-columns: 24px minmax(0, 1fr) auto auto;
		}

		.size {
			display: none;
		}
	}
</style>
