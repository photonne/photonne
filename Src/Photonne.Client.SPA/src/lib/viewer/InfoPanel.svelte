<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		addAssetTags,
		removeAssetTag,
		updateAssetDescription,
		type AssetDetailResponse
	} from '#lib/api/index.js';
	import {
		getApiAssetsByIdFacesOptions,
		getApiPeopleByIdOptions,
		getApiSearchPeopleByPersonIdAssetsOptions,
		getAssetObjectsOptions,
		getAssetScenesOptions,
		getAssetTextOptions,
		getSameDayAssetsOptions,
		getUserTagsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { dateTime, formatBytes } from '#lib/format.js';
	import { labelHref } from '#lib/library/explore-links.js';
	import { m } from '#lib/paraglide/messages.js';
	import { hasName } from '#lib/people/people.js';
	import { asUtc } from './capture-date.js';
	import DateEditor from './DateEditor.svelte';
	import { objectLabels, recognizedText, relatedItems, sceneLabels } from './extras.js';
	import { samePeoplePersonId } from './faces.js';
	import { mapHref, osmHref, photoPosition } from './location.js';
	import MiniMap from './MiniMap.svelte';
	import PanelSection from './PanelSection.svelte';
	import RecognizedText from './RecognizedText.svelte';
	import RelatedStrip from './RelatedStrip.svelte';

	interface Props {
		asset: AssetDetailResponse;
		/** Something was saved: the host refreshes what depends on it. */
		onchanged: (change: 'description' | 'date' | 'tags') => void;
		/** Opens another photo (from the related strips). */
		onopen: (assetId: string) => void;
	}

	let { asset, onchanged, onopen }: Props = $props();

	let caption = $state('');
	let newTag = $state('');
	let tags = $state<string[]>([]);
	let status = $state<'idle' | 'saved' | 'failed'>('idle');

	// Reset the editable copies whenever another asset is shown.
	$effect(() => {
		caption = asset.caption ?? '';
		tags = [...asset.userTags];
		status = 'idle';
	});

	const knownTags = createQuery(() => ({ ...getUserTagsOptions(), enabled: asset.canEdit }));

	// The extras never block the panel: a failure (or someone else's photo,
	// a 404) just leaves their section out.
	const quiet = { retry: false } as const;
	const text = createQuery(() => ({
		...getAssetTextOptions({ path: { assetId: asset.id } }),
		...quiet
	}));
	const objects = createQuery(() => ({
		...getAssetObjectsOptions({ path: { assetId: asset.id } }),
		...quiet
	}));
	const scenes = createQuery(() => ({
		...getAssetScenesOptions({ path: { assetId: asset.id } }),
		...quiet
	}));
	const sameDay = createQuery(() => ({
		...getSameDayAssetsOptions({ path: { assetId: asset.id }, query: { limit: 12 } }),
		...quiet
	}));
	const faces = createQuery(() => ({
		...getApiAssetsByIdFacesOptions({ path: { id: asset.id } }),
		...quiet
	}));
	const personId = $derived(samePeoplePersonId(faces.data ?? []));
	const person = createQuery(() => ({
		...getApiPeopleByIdOptions({ path: { id: personId ?? '' } }),
		enabled: personId !== null,
		...quiet
	}));
	const samePerson = createQuery(() => ({
		...getApiSearchPeopleByPersonIdAssetsOptions({
			path: { personId: personId ?? '' },
			query: { limit: 12 }
		}),
		enabled: personId !== null,
		...quiet
	}));

	const ocr = $derived(recognizedText(text.data ?? []));
	const objectChips = $derived(objectLabels(objects.data ?? []));
	const sceneChips = $derived(sceneLabels(scenes.data ?? []));
	const sameDayItems = $derived(relatedItems(sameDay.data?.items ?? [], asset.id));
	const samePersonItems = $derived(
		personId ? relatedItems(samePerson.data?.items ?? [], asset.id) : []
	);
	const samePersonLabel = $derived(
		person.data && hasName(person.data)
			? m.viewer_related_person({ name: person.data.name!.trim() })
			: m.viewer_related_person_unnamed()
	);

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
	const position = $derived(photoPosition(exif));
</script>

<div class="info">
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
		{#if asset.canEdit}
			<DateEditor
				{asset}
				onchanged={() => onchanged('date')}
				onstatus={(ok) => (status = ok ? 'saved' : 'failed')}
			/>
		{:else}
			<h3>{m.info_date()}</h3>
			<p>{dateTime(asUtc(asset.capturedAt))}</p>
		{/if}
	</section>

	<PanelSection id="tags" title={m.info_tags()}>
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
	</PanelSection>

	{#if objectChips.length || sceneChips.length}
		<PanelSection id="content" title={m.viewer_content_title()}>
			{#if objectChips.length}
				<ul class="chips" aria-label={m.viewer_objects()}>
					{#each objectChips as label (label)}
						<li><a href={labelHref('objects', label)}>{label}</a></li>
					{/each}
				</ul>
			{/if}
			{#if sceneChips.length}
				<ul class="chips scenes" aria-label={m.viewer_scenes()}>
					{#each sceneChips as label (label)}
						<li><a href={labelHref('scenes', label)}>{label}</a></li>
					{/each}
				</ul>
			{/if}
		</PanelSection>
	{/if}

	{#if ocr}
		<RecognizedText text={ocr} />
	{/if}

	{#if position || exif?.placeName}
		<PanelSection id="place" title={m.info_location()}>
			{#if position}
				<MiniMap lat={position.lat} lng={position.lng} />
				<div class="place">
					<a href={osmHref(position)} target="_blank" rel="noopener noreferrer"
						>{exif?.placeName ?? `${position.lat}, ${position.lng}`}</a
					>
					<a class="map-link" href={mapHref(position)}>
						<Icon name="place" size={16} />
						{m.viewer_map_open()}
					</a>
				</div>
			{:else}
				<p>{exif?.placeName}</p>
			{/if}
		</PanelSection>
	{/if}

	<PanelSection id="details" title={m.info_details()}>
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
		</dl>
	</PanelSection>

	{#if samePersonItems.length || sameDayItems.length}
		<PanelSection id="related" title={m.viewer_related_title()}>
			{#if samePersonItems.length}
				<h4>{samePersonLabel}</h4>
				<RelatedStrip label={samePersonLabel} items={samePersonItems} {onopen} />
			{/if}
			{#if sameDayItems.length}
				<h4>{m.viewer_related_day()}</h4>
				<RelatedStrip label={m.viewer_related_day()} items={sameDayItems} {onopen} />
			{/if}
		</PanelSection>
	{/if}

	<p class="status" role="status">
		{#if status === 'saved'}{m.info_saved()}{:else if status === 'failed'}{m.info_save_failed()}{/if}
	</p>
</div>

<style>
	.info {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		align-content: start;
		gap: var(--space-4);
	}

	h3,
	label {
		display: block;
		margin: 0 0 var(--space-1);
		font-size: var(--font-size-sm);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	h4 {
		margin: var(--space-1) 0 0;
		font-size: var(--font-size-xs);
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

	.tags,
	.chips {
		list-style: none;
		margin: 0;
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

	.chips a {
		display: inline-block;
		padding: 2px var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: 999px;
		color: var(--color-text);
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	.chips.scenes a {
		border-style: dashed;
	}

	.chips a:hover,
	.chips a:focus-visible {
		border-color: var(--color-accent);
		color: var(--color-accent);
	}

	.place {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.map-link {
		display: inline-flex;
		align-items: center;
		gap: 2px;
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

	dt:first-child {
		margin-top: 0;
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
