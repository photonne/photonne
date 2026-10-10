<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		cancelBackgroundTask,
		deleteApiAdminMaintenanceByKindQueue,
		type BackgroundTaskResponse
	} from '#lib/api/index.js';
	import {
		adminEnrichmentQueueSummaryOptions,
		adminEnrichmentQueueSummaryQueryKey,
		getApiAdminMaintenanceMlPendingTotalOptions,
		getBackgroundTasksOptions,
		getBackgroundTasksQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AdminPage from '#lib/admin/AdminPage.svelte';
	import '#lib/adminops/adminops.css';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import ProgressBar from '#lib/adminops/ProgressBar.svelte';
	import { Clock, toastError } from '#lib/adminops/feedback.svelte.js';
	import { icons } from '#lib/adminops/icons.js';
	import {
		enrichmentLabel,
		enrichmentTypes,
		queueKinds,
		statusLabel,
		taskTitle
	} from '#lib/adminops/labels.js';
	import { LiveTasks } from '#lib/adminops/live-tasks.svelte.js';
	import { elapsedMs, isRunning, remainingMs, sortTasks } from '#lib/adminops/tasks.js';
	import { count, duration, localDateTime, percent, timeAgo } from '#lib/adminops/time.js';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const queryClient = useQueryClient();
	const clock = new Clock();
	$effect(() => clock.start(1000));

	const tasks = createQuery(() => ({
		...getBackgroundTasksOptions(),
		refetchInterval: (query) => (query.state.data?.some(isRunning) ? 3000 : 10_000)
	}));
	const queues = createQuery(() => ({
		...adminEnrichmentQueueSummaryOptions(),
		refetchInterval: 5000
	}));
	const mlPending = createQuery(() => ({
		...getApiAdminMaintenanceMlPendingTotalOptions(),
		refetchInterval: 30_000
	}));

	const refreshTasks = () =>
		queryClient.invalidateQueries({ queryKey: getBackgroundTasksQueryKey() });

	const live = new LiveTasks(refreshTasks);
	$effect(() => live.sync(tasks.data ?? []));
	$effect(() => () => live.stop());

	const rows = $derived(sortTasks(tasks.data ?? []).map((task) => live.apply(task)));
	const runningCount = $derived(rows.filter(isRunning).length);

	const queueRows = $derived.by(() => {
		const types = queues.data?.types ?? {};
		const known: string[] = [...enrichmentTypes];
		const order = [...known, ...Object.keys(types).filter((type) => !known.includes(type))];
		return order.map((type) => ({
			type,
			counts: types[type] ?? { inQueue: 0, processing: 0, retrying: 0, failed: 0 }
		}));
	});
	const totals = $derived(
		queueRows.reduce(
			(sum, row) => ({
				inQueue: sum.inQueue + row.counts.inQueue,
				processing: sum.processing + row.counts.processing,
				retrying: sum.retrying + row.counts.retrying,
				failed: sum.failed + row.counts.failed
			}),
			{ inQueue: 0, processing: 0, retrying: 0, failed: 0 }
		)
	);

	let cancelling = $state<string | null>(null);

	async function cancel(task: BackgroundTaskResponse) {
		cancelling = task.id;
		const { error } = await cancelBackgroundTask({ path: { id: task.id } });
		cancelling = null;
		if (error) toastError(error);
		else toasts.show(m.ops_tasks_cancelled({ task: taskTitle(task) }));
		refreshTasks();
	}

	let emptying = $state<{ type: string; kind: string } | null>(null);
	let emptyBusy = $state(false);

	async function emptyQueue() {
		if (!emptying) return;
		emptyBusy = true;
		const { data, error } = await deleteApiAdminMaintenanceByKindQueue({
			path: { kind: emptying.kind }
		});
		emptyBusy = false;
		emptying = null;
		if (error || !data) return toastError(error);
		toasts.show(
			data.stillProcessing > 0
				? m.ops_queue_emptied_busy({ deleted: data.deleted, busy: data.stillProcessing })
				: m.ops_queue_emptied({ deleted: data.deleted })
		);
		queryClient.invalidateQueries({ queryKey: adminEnrichmentQueueSummaryQueryKey() });
	}

	function failuresHref(type?: string) {
		return appHref('/admin/tasks/failures') + (type ? `?type=${encodeURIComponent(type)}` : '');
	}

	/** The options a task ran with, as the server keeps them: a switch that is off says nothing. */
	function parameterChips(task: BackgroundTaskResponse) {
		return Object.entries(task.parameters ?? {})
			.filter(([key, value]) => key !== 'kind' && value.toLowerCase() !== 'false')
			.map(([key, value]) => (value.toLowerCase() === 'true' ? key : `${key}: ${value}`));
	}
</script>

<AdminPage
	title={m.ops_tasks_title()}
	description={m.ops_tasks_intro()}
	documentTitle={m.admin_tasks()}
>
	{#snippet actions()}
		<a class="btn" href={appHref('/admin/maintenance')}>
			<Icon name="build" size={18} />
			{m.ops_tasks_open_maintenance()}
		</a>
	{/snippet}

	<section class="ops-section" aria-labelledby="tasks-heading">
		<header>
			<h2 id="tasks-heading">{m.ops_tasks_background()}</h2>
			<p>
				{runningCount > 0
					? m.ops_tasks_running_count({ count: runningCount })
					: m.ops_tasks_background_hint()}
			</p>
			<div class="ops-actions">
				<button
					type="button"
					class="icon-btn"
					onclick={refreshTasks}
					aria-label={m.ops_refresh()}
					title={m.ops_refresh()}
				>
					<Icon name="refresh" size={18} />
				</button>
			</div>
		</header>

		<div class="ops-card ops-table-wrap">
			{#if tasks.isPending}
				<Skeleton variant="rows" count={3} />
			{:else if tasks.isError && !tasks.data}
				<p class="empty" role="alert">
					{m.ops_load_failed()}
					<button type="button" class="btn sm" onclick={() => tasks.refetch()}
						>{m.ops_retry()}</button
					>
				</p>
			{:else if rows.length === 0}
				<EmptyState compact iconPath={icons.queue} title={m.ops_tasks_empty()} />
			{:else}
				<table class="ops-table tasks">
					<thead>
						<tr>
							<th scope="col">{m.ops_col_task()}</th>
							<th scope="col">{m.ops_col_status()}</th>
							<th scope="col" class="progress-col">{m.ops_col_progress()}</th>
							<th scope="col">{m.ops_col_started()}</th>
							<th scope="col">{m.ops_col_duration()}</th>
							<th scope="col"><span class="visually-hidden">{m.ops_col_actions()}</span></th>
						</tr>
					</thead>
					<tbody>
						{#each rows as task (task.id)}
							{@const running = isRunning(task)}
							{@const left = running ? remainingMs(task, clock.now) : null}
							<tr>
								<th scope="row" class="name">
									{taskTitle(task)}
									{#each parameterChips(task) as chip (chip)}
										<span class="chip tag param">{chip}</span>
									{/each}
								</th>
								<td>
									<span
										class="chip tag"
										class:accent={running}
										class:success={task.status === 'Completed'}
										class:danger={task.status === 'Failed'}
										class:warning={task.status === 'Cancelled'}>{statusLabel(task.status)}</span
									>
								</td>
								<td class="progress-col">
									<div class="progress">
										<ProgressBar
											value={task.percentage}
											label={m.ops_progress_of({ task: taskTitle(task) })}
											tone={task.status === 'Failed' ? 'danger' : 'accent'}
										/>
										<span class="pct">{percent(task.percentage)}</span>
									</div>
									{#if task.lastMessage}
										<p class="message" title={task.lastMessage}>{task.lastMessage}</p>
									{/if}
								</td>
								<td class="nowrap">
									<time datetime={task.startedAt} title={localDateTime(task.startedAt)}
										>{timeAgo(task.startedAt, clock.now)}</time
									>
								</td>
								<td class="duration">
									{duration(elapsedMs(task, clock.now))}
									{#if left !== null}
										<span class="muted small"
											>{m.ops_tasks_remaining({ time: duration(left) })}</span
										>
									{/if}
								</td>
								<td class="actions">
									{#if running}
										<button
											type="button"
											class="btn sm"
											disabled={cancelling === task.id}
											onclick={() => cancel(task)}
										>
											<Icon path={icons.stop} size={16} />
											{m.ops_stop()}
										</button>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}
		</div>
	</section>

	<section class="ops-section" aria-labelledby="queues-heading">
		<header>
			<h2 id="queues-heading">{m.ops_queues_title()}</h2>
			<p>{m.ops_queues_hint()}</p>
		</header>

		<div class="summary">
			<div class="ops-card tile">
				<span class="value">{count(totals.inQueue)}</span>
				<span class="muted small">{m.ops_queue_in_queue()}</span>
			</div>
			<div class="ops-card tile">
				<span class="value">{count(totals.processing)}</span>
				<span class="muted small">{m.ops_queue_processing()}</span>
			</div>
			<div class="ops-card tile">
				<span class="value">{count(totals.retrying)}</span>
				<span class="muted small">{m.ops_queue_retrying()}</span>
			</div>
			<a class="ops-card tile link" class:alert={totals.failed > 0} href={failuresHref()}>
				<span class="value">{count(totals.failed)}</span>
				<span class="small">{m.ops_queue_failed_link()}</span>
			</a>
			{#if mlPending.data}
				<a class="ops-card tile link" href={appHref('/admin/maintenance')}>
					<span class="value">{count(mlPending.data.count)}</span>
					<span class="small">{m.ops_ml_pending_total()}</span>
				</a>
			{/if}
		</div>

		<div class="ops-card ops-table-wrap">
			{#if queues.isError && !queues.data}
				<p class="empty" role="alert">{m.ops_load_failed()}</p>
			{:else}
				<table class="ops-table">
					<thead>
						<tr>
							<th scope="col">{m.ops_col_queue()}</th>
							<th scope="col" class="num">{m.ops_queue_in_queue()}</th>
							<th scope="col" class="num">{m.ops_queue_processing()}</th>
							<th scope="col" class="num">{m.ops_queue_retrying()}</th>
							<th scope="col" class="num">{m.ops_queue_failed()}</th>
							<th scope="col"><span class="visually-hidden">{m.ops_col_actions()}</span></th>
						</tr>
					</thead>
					<tbody>
						{#each queueRows as row (row.type)}
							{@const kind = queueKinds[row.type]}
							<tr class:idle={row.counts.inQueue + row.counts.processing === 0}>
								<th scope="row" class="name">{enrichmentLabel(row.type)}</th>
								<td class="num">{count(row.counts.inQueue)}</td>
								<td class="num">
									{#if row.counts.processing > 0}
										<span class="chip tag accent">{count(row.counts.processing)}</span>
									{:else}
										0
									{/if}
								</td>
								<td class="num">{count(row.counts.retrying)}</td>
								<td class="num">
									{#if row.counts.failed > 0}
										<a href={failuresHref(row.type)}>{count(row.counts.failed)}</a>
									{:else}
										0
									{/if}
								</td>
								<td class="actions">
									{#if kind && row.counts.inQueue + row.counts.retrying > 0}
										<button
											type="button"
											class="btn sm"
											onclick={() => (emptying = { type: row.type, kind })}
										>
											<Icon path={icons.clearQueue} size={16} />
											{m.ops_queue_empty()}
										</button>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}
		</div>
	</section>
</AdminPage>

<ConfirmDialog
	open={emptying !== null}
	title={m.ops_queue_empty_confirm({ queue: emptying ? enrichmentLabel(emptying.type) : '' })}
	message={m.ops_queue_empty_confirm_text()}
	confirmLabel={m.ops_queue_empty()}
	danger
	busy={emptyBusy}
	onconfirm={emptyQueue}
	onclose={() => (emptying = null)}
/>

<style>
	.empty {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin: 0;
		padding: var(--space-6);
		color: var(--color-text-muted);
	}

	.name {
		font-weight: 600;
		white-space: nowrap;
	}

	.param {
		margin-left: var(--space-1);
		font-weight: 500;
	}

	.progress-col {
		width: 34%;
		min-width: 200px;
	}

	.nowrap {
		white-space: nowrap;
	}

	.progress {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.progress :global([role='progressbar']) {
		flex: 1;
	}

	.pct {
		min-width: 3.5em;
		text-align: right;
		font-variant-numeric: tabular-nums;
	}

	.message {
		max-width: 52ch;
		margin: var(--space-1) 0 0;
		overflow: hidden;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.duration {
		white-space: nowrap;
		font-variant-numeric: tabular-nums;
	}

	.duration span {
		display: block;
	}

	.actions {
		text-align: right;
		white-space: nowrap;
	}

	tr.idle td,
	tr.idle th {
		color: var(--color-text-muted);
	}

	.summary {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
		gap: var(--space-3);
	}

	.tile {
		display: grid;
		gap: 2px;
		padding: var(--space-3) var(--space-4);
		color: inherit;
		text-decoration: none;
	}

	.tile .value {
		font-size: var(--font-size-lg);
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}

	.tile.link {
		color: var(--color-accent);
	}

	.tile.link .value {
		color: var(--color-text);
	}

	.tile.link:hover {
		border-color: var(--color-accent);
	}

	.tile.alert .value {
		color: var(--color-danger);
	}
</style>
