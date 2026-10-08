<script lang="ts">
	import { createInfiniteQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import {
		adminRetryAllEnrichmentFailures,
		adminRetryEnrichmentFailure,
		adminSuppressEnrichmentFailure,
		type AdminEnrichmentFailureDto
	} from '#lib/api/index.js';
	import {
		adminEnrichmentQueueSummaryQueryKey,
		adminListEnrichmentFailuresInfiniteOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import '#lib/adminops/adminops.css';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { Clock, describeError, toastError } from '#lib/adminops/feedback.svelte.js';
	import { icons } from '#lib/adminops/icons.js';
	import {
		enrichmentLabel,
		enrichmentTypes,
		failureKindHint,
		failureKindLabel,
		failureKinds
	} from '#lib/adminops/labels.js';
	import { count, localDateTime, timeAgo } from '#lib/adminops/time.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const queryClient = useQueryClient();
	const clock = new Clock();
	$effect(() => clock.start());

	// The filters live in the URL, so "N con errores" elsewhere can link here
	// already filtered, and back/forward walk through them.
	const type = $derived(page.url.searchParams.get('type'));
	const kind = $derived(page.url.searchParams.get('kind'));

	const failures = createInfiniteQuery(() => ({
		...adminListEnrichmentFailuresInfiniteOptions({
			query: { type: type ?? undefined, kind: kind ?? undefined, pageSize: 50 }
		}),
		initialPageParam: {},
		getNextPageParam: (last) => last.nextCursor ?? undefined
	}));

	const first = $derived(failures.data?.pages[0]);
	// The registry moves under the cursor (a retry elsewhere, a worker
	// finishing), so a later page can repeat a row already listed.
	const items = $derived.by(() => {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- a local lookup
		const seen = new Set<string>();
		const list: AdminEnrichmentFailureDto[] = [];
		for (const item of failures.data?.pages.flatMap((p) => p.items) ?? []) {
			if (seen.has(item.taskId)) continue;
			seen.add(item.taskId);
			list.push(item);
		}
		return list;
	});

	const typeChips = $derived.by(() => {
		const counts = first?.countsByType ?? {};
		const known: string[] = [...enrichmentTypes];
		return [...known, ...Object.keys(counts).filter((t) => !known.includes(t))]
			.map((value) => ({ value, count: counts[value] ?? 0 }))
			.filter((chip) => chip.count > 0 || chip.value === type);
	});
	const kindChips = $derived.by(() => {
		const counts = first?.countsByKind ?? {};
		return failureKinds
			.map((value) => ({ value, count: counts[value] ?? 0 }))
			.filter((chip) => chip.count > 0 || chip.value === kind);
	});

	function setFilter(name: 'type' | 'kind', value: string | null) {
		const url = new URL(page.url.href);
		if (value) url.searchParams.set(name, value);
		else url.searchParams.delete(name);
		selected = [];
		goto(url.pathname + url.search, { replace: true, reset: false });
	}

	let selected = $state<string[]>([]);
	let busy = $state<string[]>([]);
	let expanded = $state<string[]>([]);

	const selectable = $derived(items.filter((item) => !busy.includes(item.taskId)));
	const allSelected = $derived(
		selectable.length > 0 && selectable.every((item) => selected.includes(item.taskId))
	);

	function toggleAll() {
		selected = allSelected ? [] : selectable.map((item) => item.taskId);
	}

	function toggleOne(id: string) {
		selected = selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id];
	}

	function refresh() {
		// Every filter's list: a retry moves counts under all of them.
		queryClient.invalidateQueries({
			predicate: (query) =>
				(query.queryKey[0] as { _id?: string })?._id === 'adminListEnrichmentFailures'
		});
		queryClient.invalidateQueries({ queryKey: adminEnrichmentQueueSummaryQueryKey() });
	}

	async function act(ids: string[], action: 'retry' | 'suppress') {
		busy = [...busy, ...ids];
		const call = action === 'retry' ? adminRetryEnrichmentFailure : adminSuppressEnrichmentFailure;
		const results = await Promise.all(ids.map((taskId) => call({ path: { taskId } })));
		busy = busy.filter((id) => !ids.includes(id));
		selected = selected.filter((id) => !ids.includes(id));
		const failed = results.filter((result) => result.error);
		const done = ids.length - failed.length;
		if (done > 0) {
			toasts.show(
				action === 'retry'
					? m.ops_failures_retried({ count: done })
					: m.ops_failures_suppressed({ count: done })
			);
		}
		if (failed.length) toastError(failed[0].error, m.ops_failures_action_failed());
		refresh();
	}

	let confirmRetryAll = $state(false);
	let retryingAll = $state(false);

	async function retryAll() {
		retryingAll = true;
		const { data, error } = await adminRetryAllEnrichmentFailures({
			query: { type: type ?? undefined, kind: kind ?? undefined }
		});
		retryingAll = false;
		confirmRetryAll = false;
		if (error || !data) return toastError(error);
		toasts.show(m.ops_failures_retried({ count: data.retried }));
		refresh();
	}

	function hideBroken(event: Event) {
		(event.currentTarget as HTMLImageElement).style.visibility = 'hidden';
	}
</script>

<svelte:head>
	<title>{m.ops_failures_title()} · {m.app_name()}</title>
</svelte:head>

<div class="ops-page">
	<header class="ops-head">
		<div>
			<a class="back" href={appHref('/admin/tasks')}>
				<Icon name="chevronLeft" size={18} />
				{m.ops_tasks_title()}
			</a>
			<h1>{m.ops_failures_title()}</h1>
			<p>{m.ops_failures_intro()}</p>
		</div>
		<div class="ops-actions">
			<button
				type="button"
				class="ops-btn primary"
				disabled={!first || first.total === 0 || retryingAll}
				onclick={() => (confirmRetryAll = true)}
			>
				<Icon name="refresh" size={18} />
				{m.ops_failures_retry_all({ count: first?.total ?? 0 })}
			</button>
		</div>
	</header>

	<div class="filters">
		<div class="chips" role="group" aria-label={m.ops_failures_filter_type()}>
			<span class="chips-label">{m.ops_failures_filter_type()}</span>
			<button
				type="button"
				class="chip"
				aria-pressed={type === null}
				onclick={() => setFilter('type', null)}>{m.ops_all()}</button
			>
			{#each typeChips as chip (chip.value)}
				<button
					type="button"
					class="chip"
					aria-pressed={type === chip.value}
					onclick={() => setFilter('type', chip.value)}
					>{enrichmentLabel(chip.value)} <span class="n">{count(chip.count)}</span></button
				>
			{/each}
		</div>
		<div class="chips" role="group" aria-label={m.ops_failures_filter_kind()}>
			<span class="chips-label">{m.ops_failures_filter_kind()}</span>
			<button
				type="button"
				class="chip"
				aria-pressed={kind === null}
				onclick={() => setFilter('kind', null)}>{m.ops_all()}</button
			>
			{#each kindChips as chip (chip.value)}
				<button
					type="button"
					class="chip"
					aria-pressed={kind === chip.value}
					title={failureKindHint(chip.value)}
					onclick={() => setFilter('kind', chip.value)}
					>{failureKindLabel(chip.value)} <span class="n">{count(chip.count)}</span></button
				>
			{/each}
		</div>
	</div>

	{#if kind}
		<p class="ops-alert">
			<Icon name="info" size={18} />
			{failureKindHint(kind)}
		</p>
	{/if}

	<section class="ops-section" aria-labelledby="list-heading">
		<header>
			<h2 id="list-heading" class="visually-hidden">{m.ops_failures_title()}</h2>
			{#if first}
				<p role="status">
					{m.ops_failures_total({ count: first.total })}
					{#if first.retrying > 0}
						· {m.ops_failures_also_retrying({ count: first.retrying })}{/if}
					{#if first.suppressed > 0}
						· {m.ops_failures_also_suppressed({ count: first.suppressed })}{/if}
				</p>
			{/if}
			{#if selected.length > 0}
				<div class="ops-actions">
					<span class="ops-small">{m.ops_selected({ count: selected.length })}</span>
					<button type="button" class="ops-btn sm" onclick={() => act([...selected], 'retry')}>
						<Icon name="refresh" size={16} />
						{m.ops_failures_retry()}
					</button>
					<button type="button" class="ops-btn sm" onclick={() => act([...selected], 'suppress')}>
						<Icon path={icons.block} size={16} />
						{m.ops_failures_suppress()}
					</button>
					<button type="button" class="ops-btn ghost sm" onclick={() => (selected = [])}
						>{m.ops_clear_selection()}</button
					>
				</div>
			{/if}
		</header>

		<div class="ops-card ops-table-wrap">
			{#if failures.isPending}
				<p class="empty">{m.ops_loading()}</p>
			{:else if failures.isError && !failures.data}
				<p class="empty" role="alert">
					{describeError(failures.error, m.ops_load_failed())}
					<button type="button" class="ops-btn sm" onclick={() => failures.refetch()}
						>{m.ops_retry()}</button
					>
				</p>
			{:else if items.length === 0}
				<p class="empty">{m.ops_failures_empty()}</p>
			{:else}
				<table class="ops-table">
					<thead>
						<tr>
							<th scope="col" class="check">
								<input
									type="checkbox"
									checked={allSelected}
									indeterminate={selected.length > 0 && !allSelected}
									onchange={toggleAll}
									aria-label={m.ops_select_all()}
								/>
							</th>
							<th scope="col">{m.ops_col_file()}</th>
							<th scope="col">{m.ops_col_task()}</th>
							<th scope="col">{m.ops_col_cause()}</th>
							<th scope="col">{m.ops_col_error()}</th>
							<th scope="col" class="num">{m.ops_col_attempts()}</th>
							<th scope="col">{m.ops_col_last_attempt()}</th>
							<th scope="col"><span class="visually-hidden">{m.ops_col_actions()}</span></th>
						</tr>
					</thead>
					<tbody>
						{#each items as item (item.taskId)}
							{@const isBusy = busy.includes(item.taskId)}
							{@const open = expanded.includes(item.taskId)}
							<tr class:busy={isBusy} class:selected={selected.includes(item.taskId)}>
								<td class="check">
									<input
										type="checkbox"
										checked={selected.includes(item.taskId)}
										disabled={isBusy}
										onchange={() => toggleOne(item.taskId)}
										aria-label={m.ops_select_item({ name: item.fileName })}
									/>
								</td>
								<td>
									<div class="file">
										<img
											src={thumbnailUrl(item.assetId, 'Small')}
											alt=""
											loading="lazy"
											onerror={hideBroken}
										/>
										<div>
											<span class="file-name" title={item.fileName}>{item.fileName}</span>
											<span class="ops-muted ops-small">{item.ownerName ?? '—'}</span>
										</div>
									</div>
								</td>
								<td class="nowrap">{enrichmentLabel(item.taskType)}</td>
								<td>
									<span class="ops-badge" title={failureKindHint(item.failureKind)}
										>{failureKindLabel(item.failureKind)}</span
									>
									{#if item.status === 'Suppressed'}
										<span class="ops-badge">{m.ops_failures_badge_suppressed()}</span>
									{:else if item.isPermanent}
										<span class="ops-badge error">{m.ops_failures_badge_permanent()}</span>
									{:else}
										<span class="ops-badge info">{m.ops_failures_badge_retrying()}</span>
									{/if}
									{#if item.failureCode}
										<code class="code">{item.failureCode}</code>
									{/if}
								</td>
								<td class="error-cell">
									{#if item.errorMessage}
										<button
											type="button"
											class="error-text"
											class:open
											aria-expanded={open}
											onclick={() =>
												(expanded = open
													? expanded.filter((id) => id !== item.taskId)
													: [...expanded, item.taskId])}>{item.errorMessage}</button
										>
									{:else}
										<span class="ops-muted">—</span>
									{/if}
								</td>
								<td class="num">{count(item.attemptCount)}</td>
								<td class="nowrap">
									{#if item.lastAttemptAt}
										<time datetime={item.lastAttemptAt} title={localDateTime(item.lastAttemptAt)}
											>{timeAgo(item.lastAttemptAt, clock.now)}</time
										>
									{:else}
										—
									{/if}
								</td>
								<td class="row-actions">
									<button
										type="button"
										class="ops-btn sm"
										disabled={isBusy}
										onclick={() => act([item.taskId], 'retry')}
										aria-label={m.ops_failures_retry_item({ name: item.fileName })}
										title={m.ops_failures_retry()}
									>
										<Icon name="refresh" size={16} />
									</button>
									{#if item.status !== 'Suppressed'}
										<button
											type="button"
											class="ops-btn sm"
											disabled={isBusy}
											onclick={() => act([item.taskId], 'suppress')}
											aria-label={m.ops_failures_suppress_item({ name: item.fileName })}
											title={m.ops_failures_suppress()}
										>
											<Icon path={icons.block} size={16} />
										</button>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}
		</div>

		{#if failures.hasNextPage}
			<div class="more">
				<button
					type="button"
					class="ops-btn"
					disabled={failures.isFetchingNextPage}
					onclick={() => failures.fetchNextPage()}>{m.ops_load_more()}</button
				>
			</div>
		{/if}
	</section>
</div>

<ConfirmDialog
	open={confirmRetryAll}
	title={m.ops_failures_retry_all({ count: first?.total ?? 0 })}
	message={m.ops_failures_retry_all_text({ count: first?.total ?? 0 })}
	confirmLabel={m.ops_failures_retry()}
	busy={retryingAll}
	onconfirm={retryAll}
	onclose={() => (confirmRetryAll = false)}
/>

<style>
	.back {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		margin-bottom: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	.back:hover {
		color: var(--color-accent);
	}

	.filters {
		display: grid;
		gap: var(--space-2);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.chips-label {
		min-width: 4.5rem;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.chip {
		padding: 2px var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: 999px;
		background: transparent;
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.chip:hover {
		background: var(--color-surface);
	}

	.chip[aria-pressed='true'] {
		border-color: var(--color-accent);
		background: color-mix(in srgb, var(--color-accent) 12%, transparent);
		color: var(--color-accent);
		font-weight: 600;
	}

	.n {
		color: var(--color-text-muted);
		font-variant-numeric: tabular-nums;
	}

	.empty {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		margin: 0;
		padding: var(--space-6);
		color: var(--color-text-muted);
	}

	.check {
		width: 36px;
	}

	.check input {
		width: 16px;
		height: 16px;
		accent-color: var(--color-accent);
	}

	tr.selected td {
		background: color-mix(in srgb, var(--color-accent) 7%, transparent);
	}

	tr.busy {
		opacity: 0.55;
	}

	.file {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-width: 200px;
	}

	.file img {
		flex: none;
		width: 40px;
		height: 40px;
		border-radius: var(--radius-sm);
		background: var(--color-placeholder);
		object-fit: cover;
	}

	.file div {
		display: grid;
		min-width: 0;
	}

	.file-name {
		max-width: 26ch;
		overflow: hidden;
		font-weight: 600;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.nowrap {
		white-space: nowrap;
	}

	.code {
		display: block;
		margin-top: 2px;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.error-cell {
		min-width: 24ch;
		max-width: 36ch;
	}

	.error-text {
		display: -webkit-box;
		padding: 0;
		overflow: hidden;
		border: none;
		background: none;
		font-size: var(--font-size-xs);
		line-clamp: 2;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		text-align: left;
		word-break: break-word;
		cursor: pointer;
	}

	.error-text.open {
		display: block;
		white-space: pre-wrap;
	}

	.row-actions {
		white-space: nowrap;
		text-align: right;
	}

	.row-actions .ops-btn + .ops-btn {
		margin-left: var(--space-1);
	}

	.more {
		display: flex;
		justify-content: center;
	}
</style>
