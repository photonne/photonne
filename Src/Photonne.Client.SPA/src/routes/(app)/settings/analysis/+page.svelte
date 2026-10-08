<script lang="ts">
	import { createInfiniteQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		retryAllEnrichmentTasks,
		retryEnrichmentTask,
		type PendingAssetDto
	} from '#lib/api/index.js';
	import { listPendingEnrichmentInfiniteOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { pendingItems, pendingSummary, retryable } from '#lib/account/settings/analysis.js';
	import { enrichmentLabel } from '#lib/adminops/labels.js';
	import { runBulk } from '#lib/albums/bulk.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';

	const queryClient = useQueryClient();
	const options = () => listPendingEnrichmentInfiniteOptions({ query: { pageSize: 50 } });
	const pending = createInfiniteQuery(() => ({
		...options(),
		initialPageParam: { query: { pageSize: 50 } },
		getNextPageParam: (last) => last.nextCursor ?? undefined
	}));

	const items = $derived(pendingItems(pending.data?.pages ?? []));
	const summary = $derived(pendingSummary(pending.data?.pages[0]));
	const failing = $derived(retryable(items));

	/** Retries running, by photo id: the whole photo ('all') or one task type. */
	let busy = $state<Record<string, string[]>>({});
	let retryingEverything = $state(false);

	function mark(assetId: string, what: string, on: boolean) {
		const current = busy[assetId] ?? [];
		busy = { ...busy, [assetId]: on ? [...current, what] : current.filter((w) => w !== what) };
	}

	const isBusy = (assetId: string, what: string) => busy[assetId]?.includes(what) ?? false;

	function refresh() {
		queryClient.invalidateQueries({ queryKey: options().queryKey });
	}

	async function retryTask(item: PendingAssetDto, taskType: string) {
		mark(item.assetId, taskType, true);
		const { error } = await retryEnrichmentTask({
			path: { id: item.assetId },
			query: { taskType }
		});
		mark(item.assetId, taskType, false);
		if (error) {
			toasts.error(m.settings_analysis_retry_failed());
			return;
		}
		toasts.show(
			m.settings_analysis_retried_task({ task: enrichmentLabel(taskType), name: item.fileName })
		);
		refresh();
	}

	async function retryAll(item: PendingAssetDto) {
		mark(item.assetId, 'all', true);
		const { data, error } = await retryAllEnrichmentTasks({ path: { id: item.assetId } });
		mark(item.assetId, 'all', false);
		if (error || !data) {
			toasts.error(m.settings_analysis_retry_failed());
			return;
		}
		toasts.show(m.settings_analysis_retried({ count: data.retried }));
		refresh();
	}

	// The server retries one photo at a time; this walks the failing photos
	// listed so far.
	async function retryEverything() {
		const targets = failing;
		retryingEverything = true;
		const outcome = await runBulk(
			targets.map((item) => item.assetId),
			async (id) => !(await retryAllEnrichmentTasks({ path: { id } })).error
		);
		retryingEverything = false;
		if (outcome.failed.length) toasts.error(m.settings_analysis_retry_failed());
		if (outcome.succeeded.length)
			toasts.show(m.settings_analysis_retried_assets({ count: outcome.succeeded.length }));
		refresh();
	}
</script>

<svelte:head>
	<title>{m.settings_analysis()} · {m.app_name()}</title>
</svelte:head>

<div class="settings-page">
	<h1>{m.settings_analysis()}</h1>
	<p class="lead">{m.settings_analysis_lead()}</p>

	{#if pending.isPending}
		<p class="hint" role="status">{m.session_restoring()}</p>
	{:else if pending.isError}
		<p class="error" role="alert">{m.error_loading()}</p>
	{:else if items.length === 0}
		<section class="card empty">
			<Icon name="check" size={32} />
			<p>{m.settings_analysis_empty()}</p>
			<div class="actions">
				<button type="button" class="button" onclick={refresh}>
					<Icon name="refresh" size={18} />{m.settings_analysis_refresh()}
				</button>
			</div>
		</section>
	{:else}
		<section class="card" aria-labelledby="analysis-summary">
			<header>
				<h2 id="analysis-summary">{m.settings_analysis_summary()}</h2>
			</header>
			<p class="counts" role="status">
				<span>{m.settings_analysis_in_flight({ count: summary.inFlight })}</span>
				<span class:bad={summary.failed > 0}>
					{m.settings_analysis_failed({ count: summary.failed })}
				</span>
			</p>
			<div class="actions">
				<button
					type="button"
					class="button primary"
					disabled={failing.length === 0 || retryingEverything}
					title={m.settings_analysis_retry_everything_hint()}
					onclick={retryEverything}
				>
					<Icon name="refresh" size={18} />
					{retryingEverything
						? m.settings_analysis_retrying()
						: m.settings_analysis_retry_everything()}
				</button>
				<button type="button" class="button" disabled={pending.isFetching} onclick={refresh}>
					{m.settings_analysis_refresh()}
				</button>
			</div>
		</section>

		<ul class="items" aria-label={m.settings_analysis_list()}>
			{#each items as item (item.assetId)}
				<li>
					<img
						src={thumbnailUrl(item.assetId, 'Small')}
						alt=""
						loading="lazy"
						decoding="async"
						width="64"
						height="64"
					/>
					<div class="about">
						<span class="name">{item.fileName}</span>
						<span class="meta">
							{dateTime(item.fileCreatedAt)} ·
							{m.settings_analysis_counts({
								pending: item.pending,
								processing: item.processing,
								failed: item.failed
							})}
						</span>
						{#if item.failedTaskTypes.length}
							<ul
								class="tasks"
								aria-label={m.settings_analysis_failed_tasks({ name: item.fileName })}
							>
								{#each item.failedTaskTypes as type (type)}
									<li>
										<button
											type="button"
											class="task"
											disabled={isBusy(item.assetId, type) || isBusy(item.assetId, 'all')}
											aria-label={m.settings_analysis_retry_task({ task: enrichmentLabel(type) })}
											title={m.settings_analysis_retry_task({ task: enrichmentLabel(type) })}
											onclick={() => retryTask(item, type)}
										>
											<Icon name="refresh" size={14} />{enrichmentLabel(type)}
										</button>
									</li>
								{/each}
							</ul>
						{/if}
					</div>
					{#if item.failed > 0 || item.failedTaskTypes.length}
						<button
							type="button"
							class="button"
							disabled={isBusy(item.assetId, 'all')}
							onclick={() => retryAll(item)}
						>
							{isBusy(item.assetId, 'all')
								? m.settings_analysis_retrying()
								: m.settings_analysis_retry_all()}
						</button>
					{/if}
				</li>
			{/each}
		</ul>

		{#if pending.hasNextPage}
			<div class="actions">
				<button
					type="button"
					class="button"
					disabled={pending.isFetchingNextPage}
					onclick={() => pending.fetchNextPage()}
				>
					{m.settings_analysis_more()}
				</button>
			</div>
		{/if}
	{/if}
</div>

<style>
	.empty {
		justify-items: center;
		text-align: center;
		color: var(--color-text-muted);
	}

	.empty p {
		margin: 0;
	}

	.counts {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2) var(--space-6);
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	.counts .bad {
		color: var(--color-danger);
	}

	.items {
		display: grid;
		margin: 0;
		padding: 0;
		list-style: none;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
	}

	.items > li {
		display: grid;
		grid-template-columns: 64px minmax(0, 1fr) auto;
		align-items: center;
		gap: var(--space-4);
		padding: var(--space-3) var(--space-4);
	}

	.items > li + li {
		border-top: 1px solid var(--color-border);
	}

	img {
		width: 64px;
		height: 64px;
		object-fit: cover;
		border-radius: var(--radius-sm);
		background: var(--color-placeholder);
	}

	.about {
		display: grid;
		gap: 2px;
		min-width: 0;
	}

	.name {
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.tasks {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
		margin: var(--space-1) 0 0;
		padding: 0;
		list-style: none;
	}

	.task {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		padding: 2px var(--space-2);
		border: 1px solid color-mix(in srgb, var(--color-danger) 50%, var(--color-border));
		border-radius: 999px;
		background: color-mix(in srgb, var(--color-danger) 8%, transparent);
		color: var(--color-text);
		font-size: var(--font-size-xs);
		cursor: pointer;
	}

	.task:hover:not(:disabled) {
		background: color-mix(in srgb, var(--color-danger) 16%, transparent);
	}

	.task:disabled {
		opacity: 0.5;
		cursor: progress;
	}

	@media (max-width: 560px) {
		.items > li {
			grid-template-columns: 48px minmax(0, 1fr);
		}

		img {
			width: 48px;
			height: 48px;
		}

		.items > li > .button {
			grid-column: 2;
			justify-self: start;
		}
	}
</style>
