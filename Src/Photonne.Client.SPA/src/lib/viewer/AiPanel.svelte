<script lang="ts">
	import { untrack } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { retryAllEnrichmentTasks, retryEnrichmentTask } from '#lib/api/index.js';
	import {
		getAssetDetailQueryKey,
		getAssetEnrichmentOptions,
		getAssetEnrichmentQueryKey,
		getAssetObjectsQueryKey,
		getAssetScenesQueryKey,
		getAssetTextQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { invalidatePeople } from '#lib/people/cache.js';
	import { icons } from '#lib/people/icons.js';
	import {
		AI_POLL_MS,
		aiAnalysisRows,
		busyTypes,
		isBusy,
		relativeTime,
		someFinished,
		type AiAnalysis,
		type AiAnalysisRow
	} from './ai-analysis.js';

	let { assetId }: { assetId: string } = $props();

	const queryClient = useQueryClient();
	let launching = $state<string[]>([]);

	const enrichment = createQuery(() => ({
		...getAssetEnrichmentOptions({ path: { id: assetId } }),
		staleTime: 0,
		// A single photo takes the workers seconds: poll while anything runs.
		refetchInterval: (query) =>
			launching.length > 0 || busyTypes(aiAnalysisRows(query.state.data?.tasks ?? [])).length
				? AI_POLL_MS
				: false
	}));

	const rows = $derived(enrichment.data ? aiAnalysisRows(enrichment.data.tasks) : null);
	const busy = $derived(rows ? busyTypes(rows) : []);
	const anyBusy = $derived(busy.length > 0 || launching.length > 0);
	const anyFailed = $derived(rows?.some((row) => row.status.kind === 'failed') ?? false);
	// Read when the rows change, for the "done 5 min ago" wording.
	const now = $derived(rows ? Date.now() : 0);

	// An analysis that stops being busy changed what the rest of the viewer
	// shows (faces, tags, text, description): refetch it.
	let wasBusy: string[] = [];
	$effect(() => {
		const current = busy;
		const before = untrack(() => wasBusy);
		wasBusy = current;
		if (!someFinished(before, current)) return;
		untrack(() => {
			const path = { path: { assetId } };
			queryClient.invalidateQueries({ queryKey: getAssetDetailQueryKey(path) });
			queryClient.invalidateQueries({ queryKey: getAssetTextQueryKey(path) });
			queryClient.invalidateQueries({ queryKey: getAssetObjectsQueryKey(path) });
			queryClient.invalidateQueries({ queryKey: getAssetScenesQueryKey(path) });
			invalidatePeople(queryClient);
		});
	});

	$effect.pre(() => {
		void assetId;
		wasBusy = [];
		launching = [];
	});

	const labels: Record<AiAnalysis['key'], () => string> = {
		faces: m.viewer_ai_faces,
		objects: m.viewer_ai_objects,
		scenes: m.viewer_ai_scenes,
		text: m.viewer_ai_text,
		embeddings: m.viewer_ai_embeddings
	};

	const rowIcons: Record<AiAnalysis['key'], string> = {
		faces: icons.face,
		objects:
			'M12 2l-5.5 9h11L12 2zm5.5 11c-2.49 0-4.5 2.01-4.5 4.5s2.01 4.5 4.5 4.5 4.5-2.01 4.5-4.5-2.01-4.5-4.5-4.5zM3 21.5h8v-8H3v8z',
		scenes: 'M14 6l-3.75 5 2.85 3.8-1.6 1.2C9.81 13.75 7 10 7 10l-6 8h22L14 6z',
		text: 'M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z',
		embeddings:
			'M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z'
	};

	function statusText(row: AiAnalysisRow) {
		const status = row.status;
		switch (status.kind) {
			case 'never':
				return m.viewer_ai_status_never();
			case 'queued':
				return m.viewer_ai_status_queued();
			case 'running':
				return m.viewer_ai_status_running();
			case 'suppressed':
				return m.viewer_ai_status_suppressed();
			case 'done':
				return status.at
					? m.viewer_ai_status_done({ when: relativeTime(status.at, now, getLocale()) })
					: m.viewer_ai_status_done_unknown();
			case 'failed':
				if (status.retryAt && status.retryAt > now) {
					return m.viewer_ai_status_failed_retry({
						when: relativeTime(status.retryAt, now, getLocale())
					});
				}
				return status.message
					? m.viewer_ai_status_failed({ message: status.message })
					: m.viewer_ai_status_failed_unknown();
		}
	}

	async function launch(analyses: readonly AiAnalysis[]) {
		const types: string[] = analyses.map((a) => a.taskType).filter((t) => !launching.includes(t));
		if (types.length === 0) return;
		launching = [...launching, ...types];
		let failed = 0;
		for (const taskType of types) {
			const { error, response } = await retryEnrichmentTask({
				path: { id: assetId },
				query: { taskType }
			});
			// 409: it's already queued or running, which is what was asked.
			if (error && response?.status !== 409) failed++;
		}
		await queryClient.invalidateQueries({
			queryKey: getAssetEnrichmentQueryKey({ path: { id: assetId } })
		});
		launching = launching.filter((t) => !types.includes(t));
		if (failed) toasts.error(m.viewer_ai_failed());
	}

	async function retryFailed() {
		const { data, error } = await retryAllEnrichmentTasks({ path: { id: assetId } });
		if (error || !data) {
			toasts.error(m.viewer_ai_failed());
			return;
		}
		toasts.show(m.viewer_ai_retried({ count: data.retried }));
		await enrichment.refetch();
	}
</script>

<div class="ai">
	<h3>{m.viewer_ai_title()}</h3>
	<p class="muted">{m.viewer_ai_subtitle()}</p>

	{#if enrichment.isPending}
		<p class="muted" role="status">{m.session_restoring()}</p>
	{:else if enrichment.isError || !rows}
		<p class="muted" role="alert">{m.error_loading()}</p>
		<button type="button" class="wide" onclick={() => enrichment.refetch()}>
			{m.viewer_ai_reload()}
		</button>
	{:else}
		<ul aria-label={m.viewer_ai_title()}>
			{#each rows as row (row.analysis.key)}
				{@const label = labels[row.analysis.key]()}
				{@const working = isBusy(row.status) || launching.includes(row.analysis.taskType)}
				<li>
					<span class="icon" aria-hidden="true"><Icon path={rowIcons[row.analysis.key]} /></span>
					<span class="what">
						<span>{label}</span>
						<small class={row.status.kind}>{statusText(row)}</small>
					</span>
					{#if working}
						<span
							class="spinner"
							role="progressbar"
							aria-label={m.viewer_ai_working({ analysis: label })}
						></span>
					{:else}
						<button
							type="button"
							class="run"
							title={row.status.kind === 'never'
								? m.viewer_ai_run({ analysis: label })
								: m.viewer_ai_rerun({ analysis: label })}
							aria-label={row.status.kind === 'never'
								? m.viewer_ai_run({ analysis: label })
								: m.viewer_ai_rerun({ analysis: label })}
							onclick={() => launch([row.analysis])}
						>
							<Icon name={row.status.kind === 'never' ? 'play' : 'refresh'} size={18} />
						</button>
					{/if}
				</li>
			{/each}
		</ul>
		<button
			type="button"
			class="wide primary"
			disabled={anyBusy}
			onclick={() => launch(rows.map((row) => row.analysis))}
		>
			{m.viewer_ai_run_all()}
		</button>
		{#if anyFailed}
			<button type="button" class="wide" disabled={anyBusy} onclick={retryFailed}>
				{m.viewer_ai_retry_failed()}
			</button>
		{/if}
	{/if}
</div>

<style>
	.ai {
		display: grid;
		gap: var(--space-3);
	}

	h3 {
		margin: 0;
		font-size: var(--font-size-sm);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	.muted {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-1);
	}

	li {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: 48px;
	}

	.icon {
		display: grid;
		color: var(--color-text-muted);
	}

	.what {
		flex: 1;
		min-width: 0;
		display: grid;
	}

	small {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		overflow-wrap: anywhere;
	}

	small.failed {
		color: var(--color-danger);
	}

	small.queued,
	small.running {
		color: var(--color-accent);
	}

	.run {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.run:hover {
		background: var(--color-surface);
	}

	.spinner {
		width: 20px;
		height: 20px;
		margin: 8px;
		border: 2px solid var(--color-border);
		border-top-color: var(--color-accent);
		border-radius: 50%;
		animation: spin 0.8s linear infinite;
	}

	@media (prefers-reduced-motion: reduce) {
		.spinner {
			animation-duration: 2.4s;
		}
	}

	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}

	.wide {
		width: 100%;
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		cursor: pointer;
	}

	.wide.primary {
		border-color: transparent;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
	}

	.wide:disabled {
		opacity: 0.5;
		cursor: default;
	}
</style>
