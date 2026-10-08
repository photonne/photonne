<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		addAssetTags,
		removeAssetTag,
		updateAssetCaptureDate,
		updateAssetDescription,
		type AssetDetailResponse
	} from '#lib/api/index.js';
	import { getUserTagsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { dateTime, formatBytes, fromDateTimeLocal, toDateTimeLocal } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';

	interface Props {
		asset: AssetDetailResponse;
		/** Something was saved: the host refreshes what depends on it. */
		onchanged: (change: 'description' | 'date' | 'tags') => void;
	}

	let { asset, onchanged }: Props = $props();

	let caption = $state('');
	let date = $state('');
	let newTag = $state('');
	let tags = $state<string[]>([]);
	let status = $state<'idle' | 'saved' | 'failed'>('idle');

	// Reset the editable copies whenever another asset is shown.
	$effect(() => {
		caption = asset.caption ?? '';
		date = toDateTimeLocal(asset.capturedAt);
		tags = [...asset.userTags];
		status = 'idle';
	});

	const knownTags = createQuery(() => ({ ...getUserTagsOptions(), enabled: asset.canEdit }));

	async function saved<T>(request: Promise<{ data?: T; error?: unknown }>) {
		const { data } = await request;
		status = data !== undefined ? 'saved' : 'failed';
		return data;
	}

	async function saveCaption() {
		const value = caption.trim();
		if (value === (asset.caption ?? '')) return;
		const data = await saved(
			updateAssetDescription({ path: { assetId: asset.id }, body: { caption: value || null } })
		);
		if (data) onchanged('description');
	}

	async function saveDate() {
		if (date === toDateTimeLocal(asset.capturedAt)) return;
		const data = await saved(
			updateAssetCaptureDate({
				path: { assetId: asset.id },
				body: { dateTaken: fromDateTimeLocal(date), writeToFile: false }
			})
		);
		if (data) onchanged('date');
	}

	async function addTag(event: SubmitEvent) {
		event.preventDefault();
		const tag = newTag.trim();
		if (!tag || tags.includes(tag)) return;
		const data = await saved(addAssetTags({ path: { assetId: asset.id }, body: { tags: [tag] } }));
		if (data) {
			tags = data.tags;
			newTag = '';
			onchanged('tags');
		}
	}

	async function removeTag(tag: string) {
		const data = await saved(removeAssetTag({ path: { assetId: asset.id, tag } }));
		if (data) {
			tags = data.tags;
			onchanged('tags');
		}
	}

	const exif = $derived(asset.exif);
	const camera = $derived([exif?.cameraMake, exif?.cameraModel].filter(Boolean).join(' ') || null);
	const exposure = $derived(
		[
			exif?.aperture ? `f/${exif.aperture}` : null,
			exif?.shutterSpeed
				? exif.shutterSpeed < 1
					? `1/${Math.round(1 / exif.shutterSpeed)} s`
					: `${exif.shutterSpeed} s`
				: null,
			exif?.focalLength ? `${exif.focalLength} mm` : null,
			exif?.iso ? `ISO ${exif.iso}` : null
		]
			.filter(Boolean)
			.join(' · ') || null
	);
	const dimensions = $derived(
		exif?.width && exif?.height ? `${exif.width} × ${exif.height}` : null
	);
	const mapUrl = $derived(
		exif?.latitude != null && exif?.longitude != null
			? `https://www.openstreetmap.org/?mlat=${exif.latitude}&mlon=${exif.longitude}#map=15/${exif.latitude}/${exif.longitude}`
			: null
	);
</script>

<aside class="panel" aria-labelledby="info-title">
	<h2 id="info-title">{m.info_title()}</h2>

	<section>
		<label for="info-caption">{m.info_description()}</label>
		{#if asset.canEdit}
			<textarea
				id="info-caption"
				rows="3"
				maxlength="2000"
				placeholder={m.info_description_placeholder()}
				bind:value={caption}
				onblur={saveCaption}></textarea>
		{:else}
			<p id="info-caption">{asset.caption || '—'}</p>
		{/if}
		{#if asset.aiDescription}
			<p class="muted">{asset.aiDescription}</p>
		{/if}
	</section>

	<section>
		<label for="info-date">{m.info_date()}</label>
		{#if asset.canEdit}
			<div class="row">
				<input id="info-date" type="datetime-local" bind:value={date} />
				<button
					type="button"
					disabled={date === toDateTimeLocal(asset.capturedAt)}
					onclick={saveDate}
				>
					{m.info_save()}
				</button>
			</div>
		{:else}
			<p id="info-date">{dateTime(asset.capturedAt)}</p>
		{/if}
	</section>

	<section>
		<h3>{m.info_tags()}</h3>
		<ul class="tags">
			{#each tags as tag (tag)}
				<li>
					{tag}
					{#if asset.canEdit}
						<button
							type="button"
							aria-label={m.info_tag_remove({ tag })}
							onclick={() => removeTag(tag)}
						>
							<Icon name="close" size={14} />
						</button>
					{/if}
				</li>
			{/each}
			{#each asset.autoTags as tag (tag)}
				<li class="auto">{tag}</li>
			{/each}
		</ul>
		{#if asset.canEdit}
			<form class="row" onsubmit={addTag}>
				<input
					aria-label={m.info_tag_add()}
					placeholder={m.info_tag_add()}
					list="known-tags"
					bind:value={newTag}
				/>
				<datalist id="known-tags">
					{#each knownTags.data ?? [] as tag (tag)}
						<option value={tag}></option>
					{/each}
				</datalist>
			</form>
		{/if}
	</section>

	<section>
		<h3>{m.info_details()}</h3>
		<dl>
			<dt>{m.info_file()}</dt>
			<dd>{asset.fileName}</dd>
			<dd class="muted">
				{formatBytes(asset.fileSize)}{dimensions ? ` · ${dimensions}` : ''}
			</dd>
			{#if asset.folderPath}
				<dt>{m.info_folder()}</dt>
				<dd>{asset.folderPath}</dd>
			{/if}
			{#if camera || exposure}
				<dt>{m.info_camera()}</dt>
				{#if camera}<dd>{camera}</dd>{/if}
				{#if exposure}<dd class="muted">{exposure}</dd>{/if}
			{/if}
			{#if exif?.placeName || mapUrl}
				<dt>{m.info_location()}</dt>
				<dd>
					{#if mapUrl}
						<a href={mapUrl} target="_blank" rel="noopener noreferrer"
							>{exif?.placeName ?? `${exif?.latitude}, ${exif?.longitude}`}</a
						>
					{:else}
						{exif?.placeName}
					{/if}
				</dd>
			{/if}
		</dl>
	</section>

	<p class="status" role="status">
		{#if status === 'saved'}{m.info_saved()}{:else if status === 'failed'}{m.info_save_failed()}{/if}
	</p>
</aside>

<style>
	.panel {
		flex: none;
		width: 340px;
		height: 100%;
		overflow-y: auto;
		padding: var(--space-4);
		background: var(--color-surface-raised);
		color: var(--color-text);
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		align-content: start;
		gap: var(--space-4);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-lg);
	}

	h3,
	label {
		display: block;
		margin: 0 0 var(--space-1);
		font-size: var(--font-size-sm);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	section p {
		margin: 0;
	}

	textarea,
	input {
		width: 100%;
		padding: var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		resize: vertical;
	}

	.row {
		display: flex;
		gap: var(--space-2);
	}

	.row input {
		flex: 1;
		min-width: 0;
	}

	.row button {
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		cursor: pointer;
	}

	.row button:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.tags {
		list-style: none;
		margin: 0 0 var(--space-2);
		padding: 0;
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
	}

	.tags li {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		padding: 2px var(--space-2);
		border-radius: 999px;
		background: var(--color-surface);
		font-size: var(--font-size-sm);
	}

	.tags li.auto {
		color: var(--color-text-muted);
	}

	.tags button {
		display: grid;
		place-items: center;
		padding: 2px;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	dl {
		margin: 0;
		display: grid;
		gap: 2px;
	}

	dt {
		margin-top: var(--space-2);
		font-size: var(--font-size-sm);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	dd {
		margin: 0;
		overflow-wrap: anywhere;
	}

	.muted {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	a {
		color: var(--color-accent);
	}

	.status {
		min-height: 1.5em;
		margin: 0;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}
</style>
