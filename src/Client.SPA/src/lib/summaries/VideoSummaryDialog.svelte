<script lang="ts">
	import { untrack } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { apiErrorCode, requestVideoSummary, uploadVideoSummaryMusic } from '#lib/api/index.js';
	import {
		getVideoSummariesOptions,
		getVideoSummariesQueryKey,
		getVideoSummaryYearsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import ProgressBar from '#lib/adminops/ProgressBar.svelte';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import {
		DEFAULT_OPTIONS,
		DURATIONS,
		findSummary,
		isActive,
		optionsOf,
		ORIENTATIONS,
		percent,
		pollWhileActive,
		requestBody,
		type SummaryOptions,
		type SummaryOrientation,
		type SummarySource
	} from './summaries.js';

	interface Props {
		/** The album to summarise; without it, the user picks a year. */
		album?: { id: string; name: string };
		onclose: () => void;
	}

	let { album, onclose }: Props = $props();

	const queryClient = useQueryClient();
	const forAlbum = untrack(() => album);

	const summaries = createQuery(() => ({
		...getVideoSummariesOptions(),
		refetchInterval: (query) => pollWhileActive(query.state.data)
	}));
	const years = createQuery(() => ({ ...getVideoSummaryYearsOptions(), enabled: !forAlbum }));

	let year = $state<number | null>(null);
	// The most recent year with videos, once the list is in.
	$effect(() => {
		if (year === null && years.data?.length) year = years.data[0].year;
	});

	const source = $derived<SummarySource | null>(
		forAlbum
			? { kind: 'album', albumId: forAlbum.id }
			: year !== null
				? { kind: 'year', year }
				: null
	);
	const current = $derived(source ? findSummary(summaries.data, source) : undefined);
	const active = $derived(isActive(current));

	// Photos off, every shape, five minutes — or whatever the last request for
	// this year or album used, once the list says.
	let options = $state<SummaryOptions>({ ...DEFAULT_OPTIONS });
	let optionsFor = '';
	$effect(() => {
		const key = source ? JSON.stringify(source) : '';
		if (!summaries.isSuccess || key === optionsFor) return;
		optionsFor = key;
		options = current ? optionsOf(current) : { ...DEFAULT_OPTIONS };
	});

	const orientationLabels: Record<SummaryOrientation, () => string> = {
		all: m.summaries_orientation_all,
		landscape: m.summaries_orientation_landscape,
		portrait: m.summaries_orientation_portrait
	};
	const orientationName = $props.id();

	let submitting = $state(false);
	let uploading = $state(false);
	let musicError = $state<string | null>(null);

	async function chooseMusic(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		input.value = '';
		if (!file) return;
		uploading = true;
		musicError = null;
		const { data, error: failure } = await uploadVideoSummaryMusic({ body: { file } });
		uploading = false;
		if (data) options.music = { id: data.id, name: data.name };
		else
			musicError =
				apiErrorCode(failure) === 'invalid_music' ? m.summaries_music_invalid() : m.action_failed();
	}
	let error = $state<string | null>(null);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (!source || submitting || active) return;
		submitting = true;
		error = null;
		const { data, error: failure } = await requestVideoSummary({
			body: requestBody(source, options)
		});
		submitting = false;
		if (data) {
			await queryClient.invalidateQueries({ queryKey: getVideoSummariesQueryKey() });
			toasts.show(m.summaries_requested({ title: data.title }));
			return;
		}
		const code = apiErrorCode(failure);
		if (code === 'not_enough_media')
			error = !options.includePhotos
				? m.summaries_not_enough_photos()
				: options.orientation !== 'all'
					? m.summaries_not_enough_orientation()
					: m.summaries_not_enough();
		else if (code === 'summary_queue_full') error = m.summaries_queue_full();
		else if (code === 'music_not_found') {
			options.music = null;
			error = m.summaries_music_missing();
		} else error = m.action_failed();
	}

	const noYears = $derived(!forAlbum && years.isSuccess && years.data.length === 0);
	const done = $derived(current?.status === 'completed' || current?.status === 'failed');
</script>

<Dialog
	open
	title={forAlbum ? m.summaries_title_album() : m.summaries_title_year()}
	{onclose}
	width="480px"
>
	<form id="summary-form" class="form" onsubmit={submit}>
		<p class="note">{m.summaries_hint()}</p>

		{#if !forAlbum}
			{#if noYears}
				<p class="note" role="status">{m.summaries_no_years()}</p>
			{:else}
				<label class="field">
					<span>{m.summaries_year()}</span>
					<select bind:value={year} disabled={!years.data}>
						{#each years.data ?? [] as row (row.year)}
							<option value={row.year}>
								{m.summaries_year_option({ year: String(row.year), videos: row.videos })}
							</option>
						{/each}
					</select>
				</label>
			{/if}
		{/if}

		<label class="field">
			<span>{m.summaries_duration()}</span>
			<select bind:value={options.durationMinutes} disabled={active}>
				{#each DURATIONS as minutes (minutes)}
					<option value={minutes}>{m.summaries_duration_option({ minutes })}</option>
				{/each}
			</select>
		</label>

		<div class="field">
			<span id="{orientationName}-label">{m.summaries_orientation()}</span>
			<div class="segmented" role="radiogroup" aria-labelledby="{orientationName}-label">
				{#each ORIENTATIONS as orientation (orientation)}
					<label>
						<input
							type="radio"
							name={orientationName}
							value={orientation}
							bind:group={options.orientation}
							disabled={active}
						/>
						{orientationLabels[orientation]()}
					</label>
				{/each}
			</div>
			<small class="note">
				{options.orientation === 'portrait'
					? m.summaries_orientation_portrait_hint()
					: options.orientation === 'landscape'
						? m.summaries_orientation_landscape_hint()
						: m.summaries_orientation_all_hint()}
			</small>
		</div>

		<label class="check">
			<input type="checkbox" bind:checked={options.showTitles} disabled={active} />
			<span>
				{m.summaries_titles()}
				<small>{m.summaries_titles_hint()}</small>
			</span>
		</label>

		<div class="field">
			<span id="{orientationName}-music">{m.summaries_music()}</span>
			{#if options.music}
				<div class="music">
					<span class="music-name">{options.music.name}</span>
					<button
						type="button"
						class="btn sm"
						disabled={active}
						onclick={() => (options.music = null)}
					>
						{m.summaries_music_remove()}
					</button>
				</div>
			{:else}
				<input
					type="file"
					accept="audio/*,.mp3,.m4a,.aac,.wav,.ogg,.oga,.opus,.flac"
					aria-labelledby="{orientationName}-music"
					disabled={active || uploading}
					onchange={chooseMusic}
				/>
			{/if}
			<small class="note">
				{uploading ? m.summaries_music_uploading() : m.summaries_music_hint()}
			</small>
			{#if musicError}<p class="error" role="alert">{musicError}</p>{/if}
		</div>

		<label class="check">
			<input type="checkbox" bind:checked={options.includePhotos} disabled={active} />
			<span>
				{m.summaries_include_photos()}
				<small>{m.summaries_include_photos_hint()}</small>
			</span>
		</label>

		{#if current}
			<div class="status" role="status">
				{#if current.status === 'pending'}
					<ProgressBar value={null} label={m.summaries_pending()} />
					<p>{m.summaries_pending()}</p>
				{:else if current.status === 'running'}
					<ProgressBar
						value={percent(current)}
						label={m.summaries_running({ percent: percent(current) })}
					/>
					<p>{m.summaries_running({ percent: percent(current) })}</p>
				{:else if current.status === 'completed' && current.assetId}
					<p>{m.summaries_ready({ title: current.title })}</p>
					<a class="btn" href={appHref(`/videos?asset=${current.assetId}`)} onclick={onclose}>
						{m.summaries_open()}
					</a>
				{:else if current.status === 'failed'}
					<p class="error">
						{current.error
							? m.summaries_failed({ error: current.error })
							: m.summaries_failed_unknown()}
					</p>
				{/if}
			</div>
			{#if done}<p class="note">{m.summaries_regenerate_hint()}</p>{/if}
		{/if}

		{#if error}<p class="error" role="alert">{error}</p>{/if}
	</form>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.summaries_close()}</button>
		<button
			type="submit"
			form="summary-form"
			class="primary"
			disabled={!source || submitting || uploading || active || noYears}
		>
			{done ? m.summaries_regenerate() : m.summaries_create()}
		</button>
	{/snippet}
</Dialog>

<style>
	.form {
		display: grid;
		gap: var(--space-4);
	}

	.note {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.segmented {
		justify-self: start;
	}

	.music {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.music-name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-weight: 600;
	}

	.check {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
		cursor: pointer;
	}

	.check input {
		margin-top: 3px;
	}

	.check small {
		display: block;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.status {
		display: grid;
		gap: var(--space-2);
		justify-items: start;
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
	}

	.status :global(.bar) {
		justify-self: stretch;
	}

	.status p {
		margin: 0;
	}

	.error {
		margin: 0;
		color: var(--color-danger);
	}
</style>
