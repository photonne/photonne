<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		apiErrorCode,
		postApiPeopleFaceRecognitionBackfill,
		postApiPeopleRecluster
	} from '#lib/api/index.js';
	import { getApiPeopleFaceRecognitionPendingCountOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { invalidatePeople } from './cache.js';

	let { open, onclose }: { open: boolean; onclose: () => void } = $props();

	const queryClient = useQueryClient();
	// The user's own counts (assets they own); refreshed while the dialog is
	// open so a queued backfill can be watched draining.
	const status = createQuery(() => ({
		...getApiPeopleFaceRecognitionPendingCountOptions(),
		enabled: open,
		staleTime: 0,
		refetchInterval: open ? 5000 : false
	}));

	let busy = $state<'backfill' | 'recluster' | null>(null);
	let disabled = $state(false);

	const number = $derived(new Intl.NumberFormat(getLocale()));
	const rows = $derived(
		status.data
			? [
					{ label: m.people_recognition_unprocessed(), value: status.data.unprocessed },
					{ label: m.people_recognition_queue(), value: status.data.inQueue },
					{ label: m.people_recognition_processing(), value: status.data.processing },
					{ label: m.people_recognition_completed(), value: status.data.completed },
					{ label: m.people_recognition_failed(), value: status.data.failed }
				]
			: []
	);

	async function backfill() {
		busy = 'backfill';
		const { data, error } = await postApiPeopleFaceRecognitionBackfill({
			body: { batchSize: null, onlyMissing: true, all: true }
		});
		busy = null;
		if (error) {
			if (apiErrorCode(error) === 'ml_task_disabled') disabled = true;
			else toasts.error(m.action_failed());
			return;
		}
		toasts.show(
			data.enqueued > 0
				? m.people_recognition_queued({ count: data.enqueued })
				: m.people_recognition_nothing()
		);
		status.refetch();
	}

	async function recluster() {
		busy = 'recluster';
		const { data, error } = await postApiPeopleRecluster();
		busy = null;
		if (error || !data) {
			toasts.error(m.action_failed());
			return;
		}
		await invalidatePeople(queryClient);
		toasts.show(
			data.personsCreated > 0
				? m.people_reclustered({ count: data.personsCreated })
				: m.people_reclustered_none()
		);
	}
</script>

<Dialog {open} title={m.people_recognition()} {onclose} width="460px">
	<p class="intro">{m.people_recognition_intro()}</p>
	{#if status.isError}
		<p role="alert">{m.error_loading()}</p>
	{:else if status.data}
		<dl>
			{#each rows as row (row.label)}
				<div>
					<dt>{row.label}</dt>
					<dd>{number.format(row.value)}</dd>
				</div>
			{/each}
		</dl>
		{#if status.data.lastCompletedAt}
			<p class="last">
				{m.people_recognition_last({ date: dateTime(status.data.lastCompletedAt) })}
			</p>
		{/if}
	{:else}
		<p role="status">{m.session_restoring()}</p>
	{/if}
	{#if disabled}
		<p class="warning" role="alert">{m.people_recognition_disabled()}</p>
	{/if}
	<div class="tools">
		<button type="button" disabled={busy !== null} onclick={backfill}>
			{m.people_recognition_backfill()}
		</button>
		<div class="recluster">
			<button
				type="button"
				disabled={busy !== null}
				aria-describedby="recluster-hint"
				onclick={recluster}
			>
				{m.people_recognition_recluster()}
			</button>
			<span id="recluster-hint">{m.people_recognition_recluster_hint()}</span>
		</div>
	</div>
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.people_close()}</button>
	{/snippet}
</Dialog>

<style>
	.intro,
	.last {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	dl {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
		gap: var(--space-2);
		margin: var(--space-4) 0 var(--space-2);
	}

	dl div {
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
	}

	dt {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	dd {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
		font-variant-numeric: tabular-nums;
	}

	.warning {
		margin: var(--space-3) 0 0;
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}

	.tools {
		display: grid;
		gap: var(--space-3);
		margin-top: var(--space-4);
		padding-top: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	.recluster {
		display: grid;
		gap: var(--space-1);
	}

	.recluster span {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.tools button {
		justify-self: start;
		padding: var(--space-2) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		cursor: pointer;
	}

	.tools button:hover {
		background: var(--color-surface);
	}

	.tools button:disabled {
		opacity: 0.5;
		cursor: progress;
	}
</style>
