<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { cancelBackgroundTask, type BackgroundTaskResponse } from '#lib/api/index.js';
	import {
		adminEnrichmentQueueSummaryOptions,
		adminIndexingCoverageOptions,
		adminIndexingCoverageQueryKey,
		getApiAdminMaintenanceMlPendingTotalQueryKey,
		getBackgroundTasksOptions,
		getBackgroundTasksQueryKey,
		getReverseGeocodePendingCountOptions,
		getReverseGeocodePendingCountQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AdminPage from '#lib/admin/AdminPage.svelte';
	import '#lib/adminops/adminops.css';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import JobRow from '#lib/adminops/JobRow.svelte';
	import QueueRow from '#lib/adminops/QueueRow.svelte';
	import TaskStatus from '#lib/adminops/TaskStatus.svelte';
	import {
		catalog,
		defaultOptions,
		faceClustering,
		sectionIds,
		sectionTitles,
		type MaintenanceTask,
		type Options,
		type SectionId
	} from '#lib/adminops/catalog.js';
	import { readCollapsed, writeCollapsed } from '#lib/adminops/collapsed.js';
	import { Clock, toastError } from '#lib/adminops/feedback.svelte.js';
	import { icons } from '#lib/adminops/icons.js';
	import { taskTitle } from '#lib/adminops/labels.js';
	import { LiveTasks } from '#lib/adminops/live-tasks.svelte.js';
	import { trigger, type StreamStarter } from '#lib/adminops/streams.js';
	import { isRunning, latestByKey } from '#lib/adminops/tasks.js';
	import { count, localDateTime, timeAgo } from '#lib/adminops/time.js';
	import Icon from '#lib/components/Icon.svelte';
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
	const summary = createQuery(() => ({
		...adminEnrichmentQueueSummaryOptions(),
		refetchInterval: 5000
	}));
	const geocode = createQuery(() => getReverseGeocodePendingCountOptions());
	const coverage = createQuery(() => adminIndexingCoverageOptions());

	const refreshTasks = () =>
		queryClient.invalidateQueries({ queryKey: getBackgroundTasksQueryKey() });
	const live = new LiveTasks(refreshTasks);
	$effect(() => live.sync(tasks.data ?? []));
	$effect(() => () => live.stop());

	const latest = $derived(latestByKey((tasks.data ?? []).map((task) => live.apply(task))));

	// Say when something this page saw running finishes, and refresh what it
	// changes: the rows only show the last line, and the admin may be elsewhere
	// on the page.
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- bookkeeping, never rendered
	const seenRunning = new Set<string>();
	$effect(() => {
		const list = tasks.data ?? [];
		for (const task of list) {
			if (isRunning(task)) seenRunning.add(task.id);
			else if (seenRunning.has(task.id)) {
				seenRunning.delete(task.id);
				finished(task);
			}
		}
	});

	function finished(task: BackgroundTaskResponse) {
		const text = task.lastMessage
			? `${taskTitle(task)}: ${task.lastMessage}`
			: m.ops_task_finished({ task: taskTitle(task) });
		if (task.status === 'Failed') toasts.error(text);
		else toasts.show(text);
		queryClient.invalidateQueries({ queryKey: adminIndexingCoverageQueryKey() });
		queryClient.invalidateQueries({ queryKey: getReverseGeocodePendingCountQueryKey() });
		queryClient.invalidateQueries({ queryKey: getApiAdminMaintenanceMlPendingTotalQueryKey() });
	}

	let options = $state<Record<string, Options>>(
		Object.fromEntries(catalog.map((task) => [task.id, defaultOptions(task)]))
	);
	let starting = $state<string[]>([]);
	let stopping = $state<string[]>([]);
	let confirming = $state<MaintenanceTask | null>(null);

	/** The options as sent: one that doesn't apply is off. */
	function effectiveOptions(task: MaintenanceTask): Options {
		const chosen = options[task.id];
		return Object.fromEntries(
			(task.options ?? []).map((option) => [
				option.id,
				chosen[option.id] === true && (option.enabledWhen?.(chosen) ?? true)
			])
		);
	}

	function requestStart(task: MaintenanceTask) {
		if (task.confirm && !task.confirm.skip?.(options[task.id])) confirming = task;
		else run(task.id, task.start?.(effectiveOptions(task)));
	}

	async function run(id: string, start: StreamStarter | undefined) {
		if (!start || starting.includes(id)) return;
		starting = [...starting, id];
		try {
			await trigger(start);
		} catch (error) {
			toastError(error);
		} finally {
			await refreshTasks();
			starting = starting.filter((s) => s !== id);
		}
	}

	async function stop(taskId: string) {
		stopping = [...stopping, taskId];
		const { error } = await cancelBackgroundTask({ path: { id: taskId } });
		if (error) toastError(error);
		await refreshTasks();
		stopping = stopping.filter((s) => s !== taskId);
	}

	function confirmRun() {
		const task = confirming;
		confirming = null;
		if (task) run(task.id, task.start?.(effectiveOptions(task)));
	}

	let collapsed = $state<SectionId[]>(readCollapsed());

	function toggleSection(id: SectionId) {
		collapsed = collapsed.includes(id) ? collapsed.filter((s) => s !== id) : [...collapsed, id];
		writeCollapsed(collapsed);
	}

	function runningIn(section: SectionId) {
		return catalog.filter(
			(task) =>
				task.section === section &&
				task.progressKey !== undefined &&
				latest.get(task.progressKey) !== undefined &&
				isRunning(latest.get(task.progressKey)!)
		).length;
	}

	const clustering = $derived(latest.get('FaceClustering'));
</script>

<AdminPage
	title={m.ops_mt_title()}
	description={m.ops_mt_intro()}
	documentTitle={m.admin_maintenance()}
>
	{#snippet actions()}
		<a class="btn" href={appHref('/admin/tasks')}>
			<Icon path={icons.queue} size={18} />
			{m.ops_mt_open_tasks()}
		</a>
	{/snippet}

	{#each sectionIds as section (section)}
		{@const open = !collapsed.includes(section)}
		{@const active = runningIn(section)}
		<section class="ops-section group" aria-labelledby="section-{section}">
			<header>
				<h2 id="section-{section}">
					<button
						type="button"
						class="fold"
						aria-expanded={open}
						aria-controls="tasks-{section}"
						onclick={() => toggleSection(section)}
					>
						<span class="chevron" class:open><Icon path={icons.expand} size={20} /></span>
						{sectionTitles[section].title()}
					</button>
				</h2>
				<p>{sectionTitles[section].hint()}</p>
				{#if !open && active > 0}
					<span class="chip tag accent">{m.ops_tasks_running_count({ count: active })}</span>
				{/if}
			</header>
			{#if open}
				<ul id="tasks-{section}" class="ops-card rows">
					{#each catalog.filter((task) => task.section === section) as task (task.id)}
						{#if task.queue}
							<QueueRow
								task={{ ...task, queue: task.queue }}
								summary={task.enrichmentType ? summary.data?.types[task.enrichmentType] : undefined}
							>
								{#snippet extra()}
									{#if task.id === 'faces'}
										<div class="extra">
											<div class="extra-head">
												<span class="small">{m.ops_mt_clustering_hint()}</span>
												<button
													type="button"
													class="btn sm"
													disabled={starting.includes('clustering') ||
														(clustering !== undefined && isRunning(clustering))}
													onclick={() => run('clustering', faceClustering)}
												>
													<Icon name="people" size={16} />
													{m.ops_mt_clustering()}
												</button>
												{#if clustering && isRunning(clustering)}
													<button
														type="button"
														class="btn sm"
														disabled={stopping.includes(clustering.id)}
														onclick={() => stop(clustering.id)}
													>
														<Icon path={icons.stop} size={16} />
														{m.ops_stop()}
													</button>
												{/if}
											</div>
											<TaskStatus
												task={clustering}
												active={m.ops_mt_clustering_active()}
												starting={starting.includes('clustering')}
												now={clock.now}
											/>
										</div>
									{/if}
								{/snippet}
							</QueueRow>
						{:else}
							<JobRow
								{task}
								latest={task.progressKey ? latest.get(task.progressKey) : undefined}
								bind:options={options[task.id]}
								starting={starting.includes(task.id)}
								stopping={(() => {
									const current = task.progressKey ? latest.get(task.progressKey) : undefined;
									return current !== undefined && stopping.includes(current.id);
								})()}
								now={clock.now}
								onstart={() => requestStart(task)}
								onstop={stop}
							>
								{#snippet extra()}
									{#if task.id === 'geocode' && geocode.data}
										{#if !geocode.data.datasetAvailable}
											<p class="notice warning">
												<Icon path={icons.warning} size={18} />
												{m.ops_mt_geocode_no_dataset()}
											</p>
										{:else}
											<p class="small muted">
												{m.ops_mt_geocode_pending({
													count: geocode.data.pending,
													cities: count(geocode.data.cities)
												})}
											</p>
										{/if}
									{:else if task.id === 'coverage' && coverage.data?.hasResult}
										{@const result = coverage.data}
										<div class="coverage">
											<p class="small">
												{#if result.verifiedAtUtc}
													<span class="muted" title={localDateTime(result.verifiedAtUtc)}
														>{m.ops_mt_coverage_verified({
															time: timeAgo(result.verifiedAtUtc, clock.now)
														})}</span
													>
												{/if}
												{m.ops_mt_coverage_result({
													total: count(result.totalFiles),
													indexed: count(result.indexed),
													unsupported: count(result.unsupported)
												})}
												{#if result.unindexed > 0}
													<span class="chip tag warning"
														>{m.ops_mt_coverage_unindexed({ count: result.unindexed })}</span
													>
												{:else}
													<span class="chip tag success">{m.ops_mt_coverage_ok()}</span>
												{/if}
											</p>
											{#if result.offlineLibraries > 0}
												<p class="small muted">
													{m.ops_mt_coverage_offline({ count: result.offlineLibraries })}
												</p>
											{/if}
											{#if result.unindexedPaths.length}
												<details>
													<summary class="small">{m.ops_mt_coverage_paths()}</summary>
													<ul class="paths">
														{#each result.unindexedPaths as path (path)}
															<li><code>{path}</code></li>
														{/each}
													</ul>
													{#if result.unindexedTruncated}
														<p class="small muted">{m.ops_mt_coverage_truncated()}</p>
													{/if}
												</details>
											{/if}
										</div>
									{/if}
								{/snippet}
							</JobRow>
						{/if}
					{/each}
				</ul>
			{/if}
		</section>
	{/each}
</AdminPage>

<ConfirmDialog
	open={confirming !== null}
	title={confirming?.confirm?.title() ?? ''}
	message={confirming?.confirm?.message() ?? ''}
	confirmLabel={m.ops_run()}
	danger
	onconfirm={confirmRun}
	onclose={() => (confirming = null)}
/>

<style>
	.group > header {
		align-items: center;
	}

	.group h2 {
		font-size: var(--font-size-lg);
	}

	.fold {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		margin-left: calc(-1 * var(--space-1));
		padding: var(--space-1);
		border: none;
		border-radius: var(--radius-sm);
		background: none;
		font: inherit;
		font-weight: 700;
		cursor: pointer;
	}

	.fold:hover {
		background: var(--color-surface);
	}

	.chevron {
		display: inline-grid;
		transform: rotate(-90deg);
		transition: transform var(--duration-fast);
	}

	.chevron.open {
		transform: none;
	}

	.rows {
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.extra {
		display: grid;
		gap: var(--space-2);
		margin-top: var(--space-1);
		padding-top: var(--space-2);
		border-top: 1px dashed var(--color-border);
	}

	.extra-head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.extra-head span {
		flex: 1 1 240px;
		color: var(--color-text-muted);
	}

	.rows :global(p.small) {
		margin: 0;
	}

	.coverage {
		display: grid;
		gap: var(--space-1);
	}

	.coverage p {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-1) var(--space-2);
		margin: 0;
	}

	.paths {
		max-height: 220px;
		margin: var(--space-2) 0 0;
		padding: var(--space-2) var(--space-3);
		overflow: auto;
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-size: var(--font-size-xs);
		list-style: none;
	}
</style>
