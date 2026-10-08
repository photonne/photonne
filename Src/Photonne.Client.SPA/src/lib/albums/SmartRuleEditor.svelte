<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		previewSmartAlbum,
		type FolderResponse,
		type SmartRuleFolderRef,
		type SmartRuleNode,
		type SmartRulePersonRef
	} from '#lib/api/index.js';
	import {
		getApiPeopleOptions,
		getFolderTreeOptions,
		listObjectLabelsOptions,
		listSceneLabelsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import MenuButton from './MenuButton.svelte';
	import MultiPicker, { type PickerOption } from './MultiPicker.svelte';
	import {
		buildRule,
		conditionTypes,
		newCondition,
		orderedRange,
		structuralTags,
		type Condition,
		type ConditionType,
		type RuleModel,
		type StructuralTag
	} from './smart-rule.js';

	interface Props {
		model: RuleModel;
		/** Names for the ids a saved rule references (GET /api/albums/{id}/rule). */
		people?: readonly SmartRulePersonRef[];
		folders?: readonly SmartRuleFolderRef[];
	}

	let { model = $bindable(), people = [], folders = [] }: Props = $props();

	const typeLabels: Record<ConditionType, () => string> = {
		person: m.albums_rule_person,
		dateRange: m.albums_rule_date,
		folder: m.albums_rule_folder,
		mediaType: m.albums_rule_media,
		favorite: m.albums_rule_favorite,
		tag: m.albums_rule_tag,
		object: m.albums_rule_object,
		scene: m.albums_rule_scene,
		text: m.albums_rule_text,
		ocr: m.albums_rule_ocr
	};

	const tagLabels: Record<StructuralTag, () => string> = {
		LivePhoto: m.albums_tag_live,
		Burst: m.albums_tag_burst,
		Panorama: m.albums_tag_panorama,
		Screenshot: m.albums_tag_screenshot,
		HDR: m.albums_tag_hdr,
		Portrait: m.albums_tag_portrait
	};

	const uses = (type: ConditionType) => model.conditions.some((c) => c.type === type);

	// Catalogs load only once a condition needs them.
	const peopleQuery = createQuery(() => ({
		...getApiPeopleOptions({ query: { limit: 1000, sort: 'name', sortDir: 'asc' } }),
		enabled: uses('person')
	}));
	const folderQuery = createQuery(() => ({ ...getFolderTreeOptions(), enabled: uses('folder') }));
	const objectQuery = createQuery(() => ({
		...listObjectLabelsOptions({ query: { limit: 500 } }),
		enabled: uses('object')
	}));
	const sceneQuery = createQuery(() => ({
		...listSceneLabelsOptions({ query: { limit: 500 } }),
		enabled: uses('scene')
	}));

	const personOptions = $derived<PickerOption[]>(
		(peopleQuery.data?.items ?? []).map((person) => ({
			value: person.id,
			label: person.name ?? m.albums_rule_unnamed(),
			hint: m.albums_items({ count: person.faceCount })
		}))
	);

	function flatten(nodes: readonly FolderResponse[]): PickerOption[] {
		return nodes.flatMap((folder) => [
			{ value: folder.id, label: folder.name, hint: folder.path },
			...flatten(folder.subFolders ?? [])
		]);
	}
	const folderOptions = $derived(flatten(folderQuery.data ?? []));

	const labelOptions = (labels: readonly { label: string; assetCount: number }[] | undefined) =>
		(labels ?? []).map((l) => ({
			value: l.label,
			label: l.label,
			hint: m.albums_items({ count: l.assetCount })
		}));
	const objectOptions = $derived(labelOptions(objectQuery.data));
	const sceneOptions = $derived(labelOptions(sceneQuery.data));

	const personNames = $derived(new Map(people.map((p) => [p.id, p.name])));
	const folderNames = $derived(new Map(folders.map((f) => [f.id, f.name])));

	function add(type: ConditionType) {
		model.conditions.push(newCondition(type));
	}

	function remove(condition: Condition) {
		model.conditions = model.conditions.filter((c) => c.id !== condition.id);
	}

	// --- Live preview: how many photos match, and a few of them ----------

	let preview = $state<{ count: number; sample: string[] } | null>(null);
	let previewState = $state<'idle' | 'loading' | 'error'>('idle');
	const rule = $derived(buildRule(model));
	const ruleKey = $derived(JSON.stringify(rule));

	// Keyed on the rule's JSON: edits that don't change what is sent (a
	// half-filled condition) don't refetch. Debounced while typing.
	$effect(() => {
		const sent = JSON.parse(ruleKey) as SmartRuleNode | null;
		if (!sent) {
			preview = null;
			previewState = 'idle';
			return;
		}
		previewState = 'loading';
		let stale = false;
		const timer = setTimeout(async () => {
			const { data } = await previewSmartAlbum({ body: { rule: sent, sampleSize: 12 } });
			if (stale) return;
			if (data) {
				preview = { count: data.count, sample: data.sampleAssetIds };
				previewState = 'idle';
			} else previewState = 'error';
		}, 350);
		return () => {
			stale = true;
			clearTimeout(timer);
		};
	});
</script>

<div class="editor">
	<div class="head">
		<label class="op">
			<span>{m.albums_rule_match()}</span>
			<select bind:value={model.op}>
				<option value="AND">{m.albums_rule_all()}</option>
				<option value="OR">{m.albums_rule_any()}</option>
			</select>
		</label>
		<MenuButton
			label={m.albums_rule_add()}
			icon="add"
			showLabel
			items={conditionTypes.map((type) => ({ label: typeLabels[type](), run: () => add(type) }))}
		/>
	</div>

	{#if model.preserved.length}
		<p class="note" role="note">{m.albums_rule_preserved({ count: model.preserved.length })}</p>
	{/if}

	{#if model.conditions.length === 0 && model.preserved.length === 0}
		<p class="note">{m.albums_rule_empty()}</p>
	{/if}

	<ol class="conditions">
		{#each model.conditions as condition (condition.id)}
			{@const title = typeLabels[condition.type]()}
			<li class="condition">
				<div class="condition-head">
					<h3>{title}</h3>
					<button
						type="button"
						class="icon"
						aria-label={m.albums_rule_remove({ condition: title })}
						title={m.albums_rule_remove({ condition: title })}
						onclick={() => remove(condition)}
					>
						<Icon name="close" size={18} />
					</button>
				</div>

				{#if condition.type === 'person'}
					<MultiPicker
						label={title}
						options={personOptions}
						selected={condition.personIds}
						loading={peopleQuery.isPending}
						unknownLabel={(id) => personNames.get(id) ?? m.albums_rule_unnamed()}
						onchange={(ids) => (condition.personIds = ids)}
					/>
					<label class="inline">
						<span>{m.albums_rule_people_match()}</span>
						<select bind:value={condition.match}>
							<option value="any">{m.albums_rule_match_any_person()}</option>
							<option value="all">{m.albums_rule_match_all_people()}</option>
						</select>
					</label>
				{:else if condition.type === 'dateRange'}
					<div class="row">
						<label class="inline">
							<span>{m.albums_rule_from()}</span>
							<input
								type="date"
								bind:value={condition.from}
								onchange={() =>
									Object.assign(condition, orderedRange(condition.from, condition.to))}
							/>
						</label>
						<label class="inline">
							<span>{m.albums_rule_to()}</span>
							<input
								type="date"
								bind:value={condition.to}
								onchange={() =>
									Object.assign(condition, orderedRange(condition.from, condition.to))}
							/>
						</label>
					</div>
				{:else if condition.type === 'folder'}
					<MultiPicker
						label={title}
						options={folderOptions}
						selected={condition.folderIds}
						loading={folderQuery.isPending}
						unknownLabel={(id) => folderNames.get(id) ?? m.albums_rule_missing_folder()}
						onchange={(ids) => (condition.folderIds = ids)}
					/>
					<label class="inline">
						<input type="checkbox" bind:checked={condition.includeSubfolders} />
						<span>{m.albums_rule_subfolders()}</span>
					</label>
				{:else if condition.type === 'mediaType'}
					<label class="inline">
						<span class="visually-hidden">{title}</span>
						<select bind:value={condition.mediaType}>
							<option value="Image">{m.albums_rule_photos()}</option>
							<option value="Video">{m.albums_rule_videos()}</option>
						</select>
					</label>
				{:else if condition.type === 'favorite'}
					<label class="inline">
						<span class="visually-hidden">{title}</span>
						<select
							value={condition.value ? 'yes' : 'no'}
							onchange={(event) => (condition.value = event.currentTarget.value === 'yes')}
						>
							<option value="yes">{m.albums_rule_is_favorite()}</option>
							<option value="no">{m.albums_rule_not_favorite()}</option>
						</select>
					</label>
				{:else if condition.type === 'tag'}
					<label class="inline">
						<span class="visually-hidden">{title}</span>
						<select bind:value={condition.tagType}>
							{#each structuralTags as tag (tag)}
								<option value={tag}>{tagLabels[tag]()}</option>
							{/each}
						</select>
					</label>
				{:else if condition.type === 'object' || condition.type === 'scene'}
					{@const query = condition.type === 'object' ? objectQuery : sceneQuery}
					<MultiPicker
						label={title}
						options={condition.type === 'object' ? objectOptions : sceneOptions}
						selected={condition.labels}
						loading={query.isPending}
						onchange={(labels) => (condition.labels = labels)}
					/>
					<label class="inline">
						<span>{m.albums_rule_labels_match()}</span>
						<select bind:value={condition.match}>
							<option value="any">{m.albums_rule_match_any_label()}</option>
							<option value="all">{m.albums_rule_match_all_labels()}</option>
						</select>
					</label>
				{:else if condition.type === 'text' || condition.type === 'ocr'}
					<label class="inline grow">
						<span class="visually-hidden">{title}</span>
						<input
							type="text"
							bind:value={condition.query}
							placeholder={condition.type === 'ocr'
								? m.albums_rule_ocr_placeholder()
								: m.albums_rule_text_placeholder()}
						/>
					</label>
				{/if}
			</li>
		{/each}
	</ol>

	<section class="preview" aria-live="polite" aria-label={m.albums_rule_preview()}>
		{#if !rule}
			<p class="note">{m.albums_rule_preview_none()}</p>
		{:else if previewState === 'error'}
			<p class="note">{m.albums_rule_preview_error()}</p>
		{:else}
			<p class="count">
				{#if preview}
					{m.albums_rule_matches({ count: preview.count })}
				{:else}
					{m.albums_rule_counting()}
				{/if}
			</p>
			{#if preview?.sample.length}
				<ul class="sample" aria-hidden="true">
					{#each preview.sample as id (id)}
						<li><img src={thumbnailUrl(id, 'Small')} alt="" loading="lazy" /></li>
					{/each}
				</ul>
			{/if}
		{/if}
	</section>
</div>

<style>
	.editor {
		display: grid;
		gap: var(--space-3);
	}

	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
	}

	.op,
	.inline {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.grow,
	.grow input {
		flex: 1;
		width: 100%;
	}

	select,
	input[type='date'],
	input[type='text'] {
		padding: var(--space-1) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.note {
		margin: 0;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.conditions {
		display: grid;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.condition {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-bg);
	}

	.condition-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	h3 {
		margin: 0;
		font-size: var(--font-size-sm);
	}

	.row {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-4);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.icon:hover {
		background: var(--color-surface);
	}

	.preview {
		display: grid;
		gap: var(--space-2);
		padding-top: var(--space-3);
		border-top: 1px solid var(--color-border);
	}

	.count {
		margin: 0;
		font-weight: 600;
	}

	.sample {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));
		gap: 4px;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.sample img {
		display: block;
		width: 100%;
		aspect-ratio: 1;
		object-fit: cover;
		border-radius: var(--radius-sm);
		background: var(--color-placeholder);
	}
</style>
