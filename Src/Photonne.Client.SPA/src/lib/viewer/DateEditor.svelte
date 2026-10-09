<script lang="ts">
	import { tick } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { updateAssetCaptureDate, type AssetDetailResponse } from '#lib/api/index.js';
	import { getAssetCaptureDateSuggestionOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime, fromDateTimeLocal, toDateTimeLocal } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import {
		asUtc,
		canWriteToFile,
		captureDateText,
		dateCandidates,
		type DateSource
	} from './capture-date.js';

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
	/** The native field only shows while changing the date: it reads in the browser's format. */
	let editing = $state(false);
	let editButton = $state<HTMLButtonElement>();

	$effect(() => {
		date = toDateTimeLocal(asUtc(asset.capturedAt));
		suggesting = false;
		editing = false;
	});

	async function cancel() {
		date = initial;
		editing = false;
		await tick();
		editButton?.focus();
	}

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
		editing = false;
		onchanged();
	}
</script>

<div class="date">
	{#if editing}
		<label for="info-date">{m.info_date()}</label>
		<!-- svelte-ignore a11y_autofocus -->
		<input
			id="info-date"
			type="datetime-local"
			bind:value={date}
			autofocus
			onkeydown={(event) => {
				// Escape leaves the field, not the viewer.
				if (event.key === 'Escape') {
					event.preventDefault();
					event.stopPropagation();
					cancel();
				}
			}}
		/>
		<div class="row">
			<button
				type="button"
				class="btn sm primary"
				disabled={date === initial || !date || saving}
				onclick={() => save(fromDateTimeLocal(date))}
			>
				{m.info_save()}
			</button>
			<button type="button" class="btn sm ghost" onclick={cancel}>{m.dialog_cancel()}</button>
		</div>
	{:else}
		<h3>{m.info_date()}</h3>
		<div class="readout">
			<time datetime={asUtc(asset.capturedAt)}>
				{captureDateText(asset.capturedAt, getLocale())}
			</time>
			<button
				type="button"
				class="icon-btn sm"
				title={m.viewer_date_change()}
				aria-label={m.viewer_date_change()}
				bind:this={editButton}
				onclick={() => (editing = true)}
			>
				<Icon name="edit" size={18} />
			</button>
		</div>
	{/if}

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
								class="btn sm"
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

	h3,
	label[for] {
		display: block;
		margin: 0;
		font-size: var(--font-size-sm);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	.readout {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
	}

	.readout time::first-letter {
		text-transform: uppercase;
	}

	.readout .icon-btn {
		margin: calc(var(--space-1) * -1) calc(var(--space-2) * -1) calc(var(--space-1) * -1) 0;
		color: var(--color-text-muted);
	}

	.readout .icon-btn:hover {
		color: var(--color-text);
	}

	.row {
		display: flex;
		gap: var(--space-2);
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
		cursor: pointer;
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
		border-radius: var(--radius-control);
		background: var(--color-surface);
		font-size: var(--font-size-sm);
	}

	.current {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
