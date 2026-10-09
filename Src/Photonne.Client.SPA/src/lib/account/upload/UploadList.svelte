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

	/** The status pill's colour: what the row's state means. */
	function tone(entry: UploadEntry) {
		switch (entry.status) {
			case 'done':
				return 'ok';
			case 'duplicate':
				return 'neutral';
			case 'uploading':
				return 'busy';
			case 'failed':
			case 'skipped':
				return 'bad';
			default:
				return 'waiting';
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
			<span class="status-cell">
				<span
					class="status {tone(entry)}"
					aria-live={entry.status === 'failed' ? 'polite' : undefined}
				>
					{statusText(entry)}
				</span>
			</span>
			<span class="actions">
				{#if entry.status === 'failed' || entry.status === 'cancelled'}
					<button
						type="button"
						class="icon-btn sm"
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
						class="icon-btn sm"
						aria-label={m.upload_cancel_one({ name })}
						title={m.upload_cancel()}
						onclick={() => queue.cancel(entry.id)}
					>
						<Icon name="close" size={18} />
					</button>
				{:else}
					<button
						type="button"
						class="icon-btn sm"
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
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
		overflow: hidden;
	}

	.row {
		position: relative;
		display: grid;
		grid-template-columns: 24px minmax(0, 1fr) auto minmax(9rem, auto) auto;
		align-items: center;
		gap: var(--space-3);
		min-height: 52px;
		padding: var(--space-1) var(--space-2) var(--space-1) var(--space-4);
		font-size: var(--font-size-sm);
		/* Long folder drops: rows off screen skip layout and paint. */
		content-visibility: auto;
		contain-intrinsic-size: auto 52px;
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
		color: var(--color-success);
	}

	.failed .kind,
	.skipped .kind {
		color: var(--color-danger);
	}

	.name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.size {
		color: var(--color-text-muted);
		white-space: nowrap;
		font-variant-numeric: tabular-nums;
	}

	.status-cell {
		display: flex;
		justify-content: flex-end;
	}

	/* Status pills: tinted by meaning, the text in the full colour (AA). */
	.status {
		display: inline-flex;
		align-items: center;
		min-height: 24px;
		padding: 0 var(--space-2);
		border-radius: 999px;
		font-size: var(--font-size-xs);
		font-weight: 600;
		white-space: nowrap;
		font-variant-numeric: tabular-nums;
	}

	.status.waiting,
	.status.neutral {
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.status.busy {
		background: var(--color-accent-soft);
		color: var(--color-text);
	}

	.status.ok {
		background: color-mix(in srgb, var(--color-success) 14%, transparent);
		color: var(--color-success);
	}

	.status.bad {
		background: color-mix(in srgb, var(--color-danger) 14%, transparent);
		color: var(--color-danger);
	}

	.cancelled .name {
		color: var(--color-text-muted);
		text-decoration: line-through;
	}

	.actions {
		display: flex;
		gap: 2px;
		min-width: 68px;
		justify-content: flex-end;
		color: var(--color-text-muted);
	}

	.actions button:hover {
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
