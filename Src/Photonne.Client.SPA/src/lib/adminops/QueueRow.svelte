<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import type { Snippet } from 'svelte';
	import { deleteApiAdminMaintenanceByKindQueue, type QueueCounts } from '#lib/api/index.js';
	import { adminEnrichmentQueueSummaryQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import ProgressBar from './ProgressBar.svelte';
	import TaskRow from './TaskRow.svelte';
	import type { BackfillKind, MaintenanceTask } from './catalog.js';
	import { toastError } from './feedback.svelte.js';
	import { icons } from './icons.js';
	import { queueProgress } from './queue-progress.js';
	import { count, duration, percent } from './time.js';

	interface Props {
		task: MaintenanceTask & { queue: BackfillKind };
		/** This queue in the enrichment summary (cheaper and fresher than the pending count). */
		summary: QueueCounts | undefined;
		extra?: Snippet;
	}

	let { task, summary, extra }: Props = $props();

	const queryClient = useQueryClient();
	const queryKey = $derived(['adminops', 'pending', task.queue.kind]);

	const pending = createQuery(() => ({
		queryKey,
		queryFn: ({ signal }) => task.queue.pending(signal),
		// Fast while the queue drains, so the bar keeps up; slow otherwise.
		refetchInterval: (query) => {
			const data = query.state.data;
			return data && data.inQueue + (data.processing ?? 0) > 0 ? 3000 : 15_000;
		}
	}));

	const counts = $derived.by(() => {
		const data = pending.data;
		return {
			unprocessed: data?.unprocessed ?? 0,
			completed: data?.completed ?? 0,
			inQueue: summary?.inQueue ?? data?.inQueue ?? 0,
			processing: summary?.processing ?? data?.processing ?? 0,
			retrying: summary?.retrying ?? data?.retrying ?? 0,
			failed: summary?.failed ?? data?.failed ?? 0
		};
	});

	/** `completed` when this page queued work: the bar measures this run, not the library. */
	let baseline = $state<number | null>(null);
	let queueing = $state(false);
	let confirmEmpty = $state(false);
	let emptying = $state(false);

	const progress = $derived(queueProgress(counts, baseline));
	const working = $derived(queueing || counts.inQueue + counts.processing > 0);

	// The run is over once its work has been seen in the queue and the queue
	// has drained (right after queueing, the counts may not show it yet).
	let sawWork = false;
	$effect(() => {
		if (baseline === null || queueing) return;
		const busy = counts.inQueue + counts.processing > 0;
		if (busy) sawWork = true;
		else if (sawWork) {
			baseline = null;
			sawWork = false;
		}
	});

	function refresh() {
		queryClient.invalidateQueries({ queryKey });
		queryClient.invalidateQueries({ queryKey: adminEnrichmentQueueSummaryQueryKey() });
	}

	async function start() {
		queueing = true;
		sawWork = false;
		baseline = counts.completed;
		try {
			const result = await task.queue.backfill({ onlyMissing: true, all: true, batchSize: null });
			if (result.enqueued > 0) {
				toasts.show(
					m.ops_queue_enqueued({
						task: task.title(),
						enqueued: count(result.enqueued),
						total: count(result.total),
						time: duration(result.elapsedMs)
					})
				);
			} else {
				baseline = null;
				toasts.show(m.ops_queue_nothing({ task: task.title() }));
			}
		} catch (error) {
			baseline = null;
			toastError(error);
		} finally {
			queueing = false;
			refresh();
		}
	}

	async function empty() {
		emptying = true;
		const { data, error } = await deleteApiAdminMaintenanceByKindQueue({
			path: { kind: task.queue.kind }
		});
		emptying = false;
		confirmEmpty = false;
		baseline = null;
		if (error || !data) toastError(error);
		else
			toasts.show(
				data.stillProcessing > 0
					? m.ops_queue_emptied_busy({ deleted: data.deleted, busy: data.stillProcessing })
					: m.ops_queue_emptied({ deleted: data.deleted })
			);
		refresh();
	}
</script>

<TaskRow
	id="task-{task.id}"
	icon={task.icon}
	title={task.title()}
	description={task.description()}
	active={working}
>
	<div class="counters">
		{#if pending.isPending}
			<span class="ops-muted">{m.ops_loading()}</span>
		{:else if pending.isError && !pending.data}
			<span class="ops-muted">{m.ops_load_failed()}</span>
		{:else}
			<span class:strong={counts.unprocessed > 0}
				>{m.ops_queue_unprocessed({ count: counts.unprocessed })}</span
			>
			<span>· {m.ops_queue_in_queue_count({ count: counts.inQueue })}</span>
			{#if counts.processing > 0}
				<span class="ops-badge info"
					>{m.ops_queue_processing_count({ count: counts.processing })}</span
				>
			{/if}
			{#if counts.retrying > 0}
				<span class="ops-badge warn">{m.ops_queue_retrying_count({ count: counts.retrying })}</span>
			{/if}
			{#if counts.failed > 0 && task.enrichmentType}
				<a
					class="ops-badge error"
					href="{appHref('/admin/tasks/failures')}?type={encodeURIComponent(task.enrichmentType)}"
					>{m.ops_queue_failed_count({ count: counts.failed })}</a
				>
			{/if}
		{/if}
	</div>

	{#if queueing}
		<div class="line"><ProgressBar value={null} label={m.ops_queue_queueing()} /></div>
		<p class="meta ops-muted">{m.ops_queue_queueing()}</p>
	{:else if progress !== null}
		<div class="line">
			<ProgressBar value={progress} label={task.active()} />
			<span class="pct">{percent(progress)}</span>
		</div>
		<p class="meta"><strong>{task.active()}</strong></p>
	{:else if working}
		<p class="meta"><strong>{task.active()}</strong></p>
	{:else if pending.data && counts.unprocessed === 0 && counts.inQueue === 0}
		<p class="meta ops-muted">{m.ops_queue_all_done()}</p>
	{/if}

	{@render extra?.()}

	{#snippet actions()}
		{#if counts.inQueue + counts.retrying > 0}
			<button
				type="button"
				class="ops-btn"
				onclick={() => (confirmEmpty = true)}
				aria-label="{m.ops_queue_empty()}: {task.title()}"
			>
				<Icon path={icons.clearQueue} size={18} />
				{m.ops_queue_empty()}
			</button>
		{/if}
		<button
			type="button"
			class="ops-btn primary"
			disabled={queueing || !pending.data || counts.unprocessed === 0}
			onclick={start}
			aria-label="{m.ops_queue_start()}: {task.title()}"
		>
			<Icon name="play" size={18} />
			{m.ops_queue_start()}
		</button>
	{/snippet}
</TaskRow>

<ConfirmDialog
	open={confirmEmpty}
	title={m.ops_queue_empty_confirm({ queue: task.title() })}
	message={m.ops_queue_empty_confirm_text()}
	confirmLabel={m.ops_queue_empty()}
	danger
	busy={emptying}
	onconfirm={empty}
	onclose={() => (confirmEmpty = false)}
/>

<style>
	.counters {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-1) var(--space-2);
		font-size: var(--font-size-sm);
	}

	.strong {
		font-weight: 600;
	}

	a.ops-badge {
		text-decoration: none;
	}

	a.ops-badge:hover {
		text-decoration: underline;
	}

	.line {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.line :global([role='progressbar']) {
		flex: 1;
	}

	.pct {
		min-width: 3.5em;
		font-size: var(--font-size-sm);
		font-variant-numeric: tabular-nums;
		text-align: right;
	}

	.meta {
		margin: 0;
		font-size: var(--font-size-xs);
	}
</style>
