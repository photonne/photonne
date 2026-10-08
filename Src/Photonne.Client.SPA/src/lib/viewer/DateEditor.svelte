<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { updateAssetCaptureDate, type AssetDetailResponse } from '#lib/api/index.js';
	import { getAssetCaptureDateSuggestionOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime, fromDateTimeLocal, toDateTimeLocal } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { asUtc, canWriteToFile, dateCandidates, type DateSource } from './capture-date.js';

	interface Props {
		asset: AssetDetailResponse;
		onchanged: () => void;
		/** A save finished: the panel shows "saved" or "failed". */
		onstatus: (ok: boolean) => void;
	}

	let { asset, onchanged, onstatus }: Props = $props();

	let date = $state('');
	let writeToFile = $state(false);
	let saving = $state(false);
	let suggesting = $state(false);

	$effect(() => {
		date = toDateTimeLocal(asUtc(asset.capturedAt));
		suggesting = false;
	});

	const writable = $derived(canWriteToFile(asset));
	const initial = $derived(toDateTimeLocal(asUtc(asset.capturedAt)));

	// Only on request: the server re-reads the file from disk for it.
	const suggestion = createQuery(() => ({
		...getAssetCaptureDateSuggestionOptions({ path: { assetId: asset.id } }),
		enabled: suggesting,
		staleTime: 0
	}));
	const candidates = $derived(dateCandidates(suggestion.data));

	const sourceLabels: Record<DateSource, () => string> = {
		exif: m.viewer_date_source_exif,
		file: m.viewer_date_source_file,
		fileName: m.viewer_date_source_filename,
		folder: m.viewer_date_source_folder
	};

	const originLabels: Record<string, () => string> = {
		FileSystem: m.viewer_date_origin_filesystem,
		Inferred: m.viewer_date_origin_inferred,
		Exif: m.viewer_date_origin_exif,
		Manual: m.viewer_date_origin_manual
	};

	async function save(iso: string) {
		if (saving) return;
		saving = true;
		const { data } = await updateAssetCaptureDate({
			path: { assetId: asset.id },
			body: { dateTaken: iso, writeToFile: writable && writeToFile }
		});
		saving = false;
		onstatus(data !== undefined);
		if (!data) return;
		if (writable && writeToFile && !data.fileWritten) {
			toasts.error(
				data.reason
					? m.viewer_date_not_written({ reason: data.reason })
					: m.viewer_date_not_written_unknown()
			);
		}
		suggesting = false;
		onchanged();
	}
</script>

<div class="date">
	<label for="info-date">{m.info_date()}</label>
	<div class="row">
		<input id="info-date" type="datetime-local" bind:value={date} />
		<button
			type="button"
			disabled={date === initial || !date || saving}
			onclick={() => save(fromDateTimeLocal(date))}
		>
			{m.info_save()}
		</button>
	</div>

	{#if writable}
		<label class="check">
			<input type="checkbox" bind:checked={writeToFile} />
			<span>
				{m.viewer_date_write_file()}
				<small>{m.viewer_date_write_file_hint()}</small>
			</span>
		</label>
	{/if}

	{#if !suggesting}
		<button type="button" class="link" onclick={() => (suggesting = true)}>
			{m.viewer_date_suggest()}
		</button>
	{:else if suggestion.isPending}
		<p class="muted" role="status">{m.viewer_date_suggest_loading()}</p>
	{:else if suggestion.isError}
		<p class="muted" role="alert">{m.error_loading()}</p>
	{:else if suggestion.data}
		{@const origin = originLabels[suggestion.data.currentSource]}
		{#if origin}
			<p class="muted">{m.viewer_date_current_origin({ origin: origin() })}</p>
		{/if}
		{#if candidates.length === 0}
			<p class="muted">{m.viewer_date_suggest_none()}</p>
		{:else}
			<ul class="candidates" aria-label={m.viewer_date_suggestions()}>
				{#each candidates as candidate (candidate.source)}
					<li>
						<span class="when">
							<span>{dateTime(candidate.date)}</span>
							<small>{sourceLabels[candidate.source]()}</small>
						</span>
						{#if candidate.current}
							<span class="current">{m.viewer_date_current()}</span>
						{:else}
							<button
								type="button"
								disabled={saving}
								aria-label={m.viewer_date_apply_label({
									date: dateTime(candidate.date),
									source: sourceLabels[candidate.source]()
								})}
								onclick={() => save(candidate.date)}
							>
								{m.viewer_date_apply()}
							</button>
						{/if}
					</li>
				{/each}
			</ul>
		{/if}
	{/if}
</div>

<style>
	.date {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: var(--space-2);
	}

	label[for] {
		display: block;
		font-size: var(--font-size-sm);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	.row {
		display: flex;
		gap: var(--space-2);
	}

	input[type='datetime-local'] {
		flex: 1;
		min-width: 0;
		padding: var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	button {
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		cursor: pointer;
	}

	button:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.check {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.check input {
		margin-top: 3px;
	}

	.check span,
	.when {
		display: grid;
	}

	small {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.link {
		justify-self: start;
		padding: 0;
		border: 0;
		background: none;
		color: var(--color-accent);
		font-size: var(--font-size-sm);
	}

	.link:hover {
		text-decoration: underline;
	}

	.muted {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.candidates {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-1);
	}

	.candidates li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
		padding: var(--space-2);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-size: var(--font-size-sm);
	}

	.candidates button {
		padding: var(--space-1) var(--space-2);
		background: var(--color-bg);
	}

	.current {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
