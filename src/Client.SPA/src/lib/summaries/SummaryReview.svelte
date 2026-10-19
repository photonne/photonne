<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		apiErrorCode,
		renderVideoSummary,
		updateVideoSummaryPlan,
		getVideoSummaryPlanAlternatives,
		type VideoSummaryPlanCandidateResponse
	} from '#lib/api/index.js';
	import {
		getVideoSummariesQueryKey,
		getVideoSummaryOptions,
		getVideoSummaryPlanOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import ProgressBar from '#lib/adminops/ProgressBar.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import {
		captureDay,
		clock,
		MAX_PHOTO_SECONDS,
		MIN_PHOTO_SECONDS,
		problem,
		setEnd,
		setPhotoSeconds,
		setStart,
		swapIn,
		togglePinned,
		toEditable,
		toRequest,
		totalSeconds,
		type EditableClip
	} from './review.js';
	import { isActive, percent, POLL_MS } from './summaries.js';

	interface Props {
		id: string;
	}

	let { id }: Props = $props();

	const queryClient = useQueryClient();
	const plan = createQuery(() => getVideoSummaryPlanOptions({ path: { summaryId: id } }));
	const summary = createQuery(() => ({
		...getVideoSummaryOptions({ path: { summaryId: id } }),
		refetchInterval: (query) => (isActive(query.state.data) ? POLL_MS : false)
	}));

	let clips = $state<EditableClip[]>([]);
	let loadedFrom: unknown = null;
	$effect(() => {
		if (!plan.data || plan.data === loadedFrom) return;
		loadedFrom = plan.data;
		clips = toEditable(plan.data);
	});

	const target = $derived(plan.data?.targetSeconds ?? 0);
	const total = $derived(totalSeconds(clips));
	const issue = $derived(plan.data ? problem(clips, target) : null);
	const busy = $derived(isActive(summary.data));

	let saving = $state(false);
	let error = $state<string | null>(null);

	function replace(key: string, next: EditableClip) {
		clips = clips.map((c) => (c.key === key ? next : c));
	}

	function remove(key: string) {
		clips = clips.filter((c) => c.key !== key);
		if (swapping === key) swapping = null;
	}

	// The clip being swapped, and what could take its place.
	let swapping = $state<string | null>(null);
	let alternatives = $state<VideoSummaryPlanCandidateResponse[] | null>(null);
	async function openSwap(clip: EditableClip) {
		if (swapping === clip.key) {
			swapping = null;
			return;
		}
		swapping = clip.key;
		alternatives = null;
		const { data } = await getVideoSummaryPlanAlternatives({
			path: { summaryId: id },
			query: { near: clip.assetId, count: 24 }
		});
		if (swapping !== clip.key) return;
		// Not what's already in the cut as edited here.
		const used = new Set(clips.map((c) => c.assetId));
		alternatives = (data ?? []).filter((a) => !used.has(a.assetId));
	}

	function swap(clip: EditableClip, alternative: VideoSummaryPlanCandidateResponse) {
		replace(clip.key, swapIn(clip, alternative));
		swapping = null;
	}

	async function save(): Promise<boolean> {
		error = null;
		const { data, error: failure } = await updateVideoSummaryPlan({
			path: { summaryId: id },
			body: { clips: toRequest(clips) }
		});
		if (data) {
			loadedFrom = data;
			queryClient.setQueryData(
				getVideoSummaryPlanOptions({ path: { summaryId: id } }).queryKey,
				data
			);
			return true;
		}
		const code = apiErrorCode(failure);
		error =
			code === 'plan_too_long'
				? m.summaries_review_too_long()
				: code === 'not_enough_media'
					? m.summaries_review_too_few()
					: code === 'summary_busy'
						? m.summaries_review_busy()
						: m.action_failed();
		return false;
	}

	async function saveOnly() {
		if (saving || issue) return;
		saving = true;
		if (await save()) toasts.show(m.summaries_review_saved());
		saving = false;
	}

	async function render() {
		if (saving || issue || busy) return;
		saving = true;
		if (await save()) {
			const { data, error: failure } = await renderVideoSummary({ path: { summaryId: id } });
			if (data) {
				queryClient.setQueryData(
					getVideoSummaryOptions({ path: { summaryId: id } }).queryKey,
					data
				);
				await queryClient.invalidateQueries({ queryKey: getVideoSummariesQueryKey() });
				toasts.show(m.summaries_requested({ title: data.title }));
			} else
				error =
					apiErrorCode(failure) === 'summary_queue_full'
						? m.summaries_queue_full()
						: m.action_failed();
		}
		saving = false;
	}

	const locale = getLocale();
</script>

<div class="page">
	<PageHeader
		title={plan.data?.title ?? m.summaries_review_title()}
		subtitle={m.summaries_review_subtitle()}
	>
		{#snippet actions()}
			<button
				type="button"
				class="btn"
				disabled={!plan.data || saving || !!issue || busy}
				onclick={saveOnly}
			>
				{m.summaries_review_save()}
			</button>
			<button
				type="button"
				class="btn primary"
				disabled={!plan.data || saving || !!issue || busy}
				onclick={render}
			>
				{m.summaries_review_render()}
			</button>
		{/snippet}
	</PageHeader>

	{#if plan.isPending}
		<Skeleton variant="cards" count={6} />
	{:else if plan.isError}
		<div role="alert"><EmptyState icon="info" title={m.summaries_review_missing()} /></div>
	{:else}
		<div class="summary-bar">
			<div class="total" class:over={issue === 'too_long'}>
				{m.summaries_review_total({ total: clock(total), target: clock(target) })}
				· {m.summaries_review_clips({ count: clips.length })}
			</div>
			{#if summary.data && busy}
				<div class="status" role="status">
					<ProgressBar
						value={summary.data.status === 'running' ? percent(summary.data) : null}
						label={summary.data.status === 'running'
							? m.summaries_running({ percent: percent(summary.data) })
							: m.summaries_pending()}
					/>
					<span>
						{summary.data.status === 'running'
							? m.summaries_running({ percent: percent(summary.data) })
							: m.summaries_pending()}
					</span>
				</div>
			{:else if summary.data?.status === 'completed' && summary.data.assetId}
				<div class="status" role="status">
					<span>{m.summaries_ready({ title: summary.data.title })}</span>
					<a class="btn sm" href={appHref(`/videos?asset=${summary.data.assetId}`)}>
						{m.summaries_open()}
					</a>
				</div>
			{/if}
			{#if issue === 'too_long'}
				<p class="error" role="alert">{m.summaries_review_too_long()}</p>
			{:else if issue === 'too_few'}
				<p class="error" role="alert">{m.summaries_review_too_few()}</p>
			{:else if error}
				<p class="error" role="alert">{error}</p>
			{/if}
		</div>

		<ol class="clips" aria-label={m.summaries_review_list()}>
			{#each clips as clip, index (clip.key)}
				<li class="clip" class:pinned={clip.pinned}>
					<div class="media">
						{#if clip.isVideo}
							<!-- svelte-ignore a11y_media_has_caption -->
							<video
								src="/api/assets/{clip.assetId}/content#t={clip.start},{clip.start + clip.length}"
								poster={thumbnailUrl(clip.assetId, 'Medium')}
								preload="none"
								controls
								aria-label={m.summaries_review_preview({ number: index + 1 })}
							></video>
						{:else}
							<img src={thumbnailUrl(clip.assetId, 'Medium')} alt="" loading="lazy" />
						{/if}
					</div>
					<div class="body">
						<div class="meta">
							<strong>{index + 1}</strong>
							<span>{captureDay(clip.capturedAt, locale)}</span>
							<span class="kind">
								{clip.isVideo ? m.summaries_review_video() : m.summaries_review_photo()}
							</span>
						</div>

						{#if clip.isVideo}
							<div class="times">
								<label>
									<span>{m.summaries_review_start()}</span>
									<input
										type="number"
										min="0"
										max={clip.sourceSeconds}
										step="0.5"
										value={clip.start}
										disabled={busy}
										onchange={(e) =>
											replace(clip.key, setStart(clip, e.currentTarget.valueAsNumber))}
									/>
								</label>
								<label>
									<span>{m.summaries_review_end()}</span>
									<input
										type="number"
										min="0"
										max={clip.sourceSeconds}
										step="0.5"
										value={Math.round((clip.start + clip.length) * 10) / 10}
										disabled={busy}
										onchange={(e) => replace(clip.key, setEnd(clip, e.currentTarget.valueAsNumber))}
									/>
								</label>
								<small>
									{m.summaries_review_of({
										length: clock(clip.length),
										source: clock(clip.sourceSeconds)
									})}
								</small>
							</div>
						{:else}
							<div class="times">
								<label>
									<span>{m.summaries_review_seconds()}</span>
									<input
										type="number"
										min={MIN_PHOTO_SECONDS}
										max={MAX_PHOTO_SECONDS}
										step="0.5"
										value={clip.length}
										disabled={busy}
										onchange={(e) =>
											replace(clip.key, setPhotoSeconds(clip, e.currentTarget.valueAsNumber))}
									/>
								</label>
							</div>
						{/if}

						<div class="buttons">
							<button
								type="button"
								class="btn sm"
								aria-pressed={clip.pinned}
								disabled={busy}
								onclick={() => (clips = togglePinned(clips, clip.assetId))}
							>
								{m.summaries_review_pin()}
							</button>
							<button
								type="button"
								class="btn sm"
								aria-expanded={swapping === clip.key}
								disabled={busy}
								onclick={() => openSwap(clip)}
							>
								{m.summaries_review_swap()}
							</button>
							<button type="button" class="btn sm" disabled={busy} onclick={() => remove(clip.key)}>
								{m.summaries_review_remove()}
							</button>
						</div>

						{#if swapping === clip.key}
							<div class="alternatives" role="group" aria-label={m.summaries_review_alternatives()}>
								{#if alternatives === null}
									<p class="note">{m.summaries_review_loading()}</p>
								{:else if alternatives.length === 0}
									<p class="note">{m.summaries_review_no_alternatives()}</p>
								{:else}
									{#each alternatives as alternative (alternative.assetId)}
										<button
											type="button"
											class="alternative"
											onclick={() => swap(clip, alternative)}
											aria-label={m.summaries_review_use({
												kind: alternative.isVideo
													? m.summaries_review_video()
													: m.summaries_review_photo(),
												date: captureDay(alternative.capturedAt, locale)
											})}
										>
											<img src={thumbnailUrl(alternative.assetId, 'Small')} alt="" loading="lazy" />
											{#if alternative.isVideo}
												<span class="duration">{clock(alternative.durationSeconds)}</span>
											{/if}
										</button>
									{/each}
								{/if}
							</div>
						{/if}
					</div>
				</li>
			{/each}
		</ol>
	{/if}
</div>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.summary-bar {
		display: grid;
		gap: var(--space-2);
		padding: 0 var(--page-gutter) var(--space-3);
	}

	.total {
		font-weight: 600;
	}

	.total.over {
		color: var(--color-danger);
	}

	.status {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	.status :global(.bar) {
		flex: 1 1 200px;
	}

	.clips {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
		gap: var(--space-4);
		margin: 0;
		padding: 0 var(--page-gutter);
		list-style: none;
	}

	.clip {
		display: grid;
		grid-template-rows: auto 1fr;
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
	}

	.clip.pinned {
		border-color: var(--color-accent);
	}

	.media {
		aspect-ratio: 16 / 9;
		background: #000;
	}

	.media video,
	.media img {
		width: 100%;
		height: 100%;
		object-fit: contain;
		display: block;
	}

	.body {
		display: grid;
		gap: var(--space-3);
		align-content: start;
		padding: var(--space-3);
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
		align-items: baseline;
	}

	.kind,
	.note,
	.times small {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.note {
		margin: 0;
	}

	.times {
		display: flex;
		flex-wrap: wrap;
		align-items: end;
		gap: var(--space-3);
	}

	.times label {
		display: grid;
		gap: var(--space-1);
		font-size: var(--font-size-sm);
	}

	.times input {
		width: 6em;
	}

	.buttons {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.buttons [aria-pressed='true'] {
		border-color: var(--color-accent);
		color: var(--color-accent);
	}

	.alternatives {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
		gap: var(--space-2);
	}

	.alternative {
		position: relative;
		aspect-ratio: 1;
		padding: 0;
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		cursor: pointer;
	}

	.alternative img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
	}

	.duration {
		position: absolute;
		right: 4px;
		bottom: 4px;
		padding: 0 4px;
		border-radius: 3px;
		background: rgb(0 0 0 / 60%);
		color: #fff;
		font-size: 11px;
	}

	.error {
		margin: 0;
		color: var(--color-danger);
	}
</style>
