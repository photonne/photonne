<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import type { FolderResponse } from '#lib/api/index.js';
	import {
		getApiPeopleOptions,
		getFolderTreeOptions,
		listObjectLabelsOptions,
		listSceneLabelsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import CheckList, { type CheckOption } from '../CheckList.svelte';
	import FilterMenu from '../FilterMenu.svelte';
	import { labelText } from '../text.js';
	import {
		conditionNode,
		conditionTypes,
		newCondition,
		structuralTags,
		type Condition,
		type ConditionType
	} from './rule-model.js';

	interface Props {
		op: 'AND' | 'OR';
		conditions: Condition[];
	}

	let { op = $bindable(), conditions = $bindable() }: Props = $props();

	const typeLabels: Record<ConditionType, () => string> = {
		dateRange: m.organize_rule_date,
		folder: m.organize_rule_folder,
		person: m.organize_rule_person,
		mediaType: m.organize_rule_media,
		favorite: m.organize_rule_favorite,
		tag: m.organize_rule_tag,
		object: m.organize_rule_object,
		scene: m.organize_rule_scene,
		text: m.organize_rule_text,
		ocr: m.organize_rule_ocr
	};

	const tagLabels: Record<string, () => string> = {
		LivePhoto: m.organize_tag_live,
		Burst: m.organize_tag_burst,
		Panorama: m.organize_tag_panorama,
		Screenshot: m.organize_tag_screenshot,
		HDR: m.organize_tag_hdr,
		Portrait: m.organize_tag_portrait
	};

	const people = createQuery(() => getApiPeopleOptions({ query: { limit: 200 } }));
	const objects = createQuery(() => listObjectLabelsOptions({ query: { limit: 1000 } }));
	const scenes = createQuery(() => listSceneLabelsOptions({ query: { limit: 1000 } }));
	const folders = createQuery(() => getFolderTreeOptions());

	function flatten(nodes: readonly FolderResponse[]): FolderResponse[] {
		return nodes.flatMap((folder) => [folder, ...flatten(folder.subFolders ?? [])]);
	}

	const options = $derived<Record<'person' | 'object' | 'scene' | 'folder', CheckOption[]>>({
		person: (people.data?.items ?? []).map((person) => ({
			value: person.id,
			label: person.name ?? m.search_person_unnamed(),
			hint: String(person.faceCount)
		})),
		object: (objects.data ?? []).map((item) => ({
			value: item.label,
			label: labelText(item.label),
			hint: String(item.assetCount)
		})),
		scene: (scenes.data ?? []).map((item) => ({
			value: item.label,
			label: labelText(item.label),
			hint: String(item.assetCount)
		})),
		folder: flatten(folders.data ?? []).map((folder) => ({
			value: folder.id,
			label: folder.path
		}))
	});

	let nextKey = 0;
	let adding = $state('');

	function add(type: ConditionType) {
		conditions = [...conditions, newCondition(type, `c${nextKey++}`)];
	}

	function update(key: string, change: Partial<Condition>) {
		conditions = conditions.map((condition) =>
			condition.key === key ? { ...condition, ...change } : condition
		);
	}

	function toggle(list: readonly string[], value: string) {
		return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
	}

	/** "3 elegidas" on a picker's button. */
	function pickedLabel(count: number) {
		return count ? m.organize_rule_picked({ count }) : m.organize_rule_pick();
	}
</script>

<div class="editor">
	<div class="head">
		<label>
			{m.organize_rule_match()}
			<select bind:value={op}>
				<option value="AND">{m.organize_rule_all()}</option>
				<option value="OR">{m.organize_rule_any()}</option>
			</select>
		</label>
		<label>
			<span class="visually-hidden">{m.organize_rule_add()}</span>
			<select
				bind:value={adding}
				onchange={() => {
					if (adding) add(adding as ConditionType);
					adding = '';
				}}
			>
				<option value="">{m.organize_rule_add()}</option>
				{#each conditionTypes as type (type)}
					<option value={type}>{typeLabels[type]()}</option>
				{/each}
			</select>
		</label>
	</div>

	{#if conditions.length === 0}
		<p class="empty">{m.organize_rule_empty()}</p>
	{/if}

	<ol>
		{#each conditions as condition (condition.key)}
			{@const label = typeLabels[condition.type]()}
			<li class:incomplete={!conditionNode(condition)}>
				<div class="row-head">
					<strong>{label}</strong>
					<label class="negate">
						<input
							type="checkbox"
							checked={condition.negate}
							onchange={(event) => update(condition.key, { negate: event.currentTarget.checked })}
						/>
						{m.organize_rule_negate()}
					</label>
					<button
						type="button"
						class="icon-btn sm"
						aria-label={m.organize_rule_remove({ condition: label })}
						onclick={() => (conditions = conditions.filter((c) => c.key !== condition.key))}
					>
						<Icon name="close" size={18} />
					</button>
				</div>

				<div class="control">
					{#if condition.type === 'dateRange'}
						<label>
							{m.search_filter_from()}
							<input
								type="date"
								value={condition.from ?? ''}
								onchange={(event) =>
									update(condition.key, { from: event.currentTarget.value || null })}
							/>
						</label>
						<label>
							{m.search_filter_to()}
							<input
								type="date"
								value={condition.to ?? ''}
								onchange={(event) =>
									update(condition.key, { to: event.currentTarget.value || null })}
							/>
						</label>
					{:else if condition.type === 'folder' || condition.type === 'person'}
						{@const kind = condition.type}
						<FilterMenu name={label} label={pickedLabel(condition.ids.length)} width="360px">
							<CheckList
								{label}
								options={options[kind]}
								selected={condition.ids}
								ontoggle={(id) => update(condition.key, { ids: toggle(condition.ids, id) })}
							/>
						</FilterMenu>
						{#if kind === 'folder'}
							<label class="inline">
								<input
									type="checkbox"
									checked={condition.includeSubfolders}
									onchange={(event) =>
										update(condition.key, { includeSubfolders: event.currentTarget.checked })}
								/>
								{m.organize_rule_subfolders()}
							</label>
						{/if}
					{:else if condition.type === 'object' || condition.type === 'scene'}
						{@const kind = condition.type}
						<FilterMenu name={label} label={pickedLabel(condition.labels.length)}>
							<CheckList
								{label}
								options={options[kind]}
								selected={condition.labels}
								ontoggle={(value) =>
									update(condition.key, { labels: toggle(condition.labels, value) })}
							/>
						</FilterMenu>
					{:else if condition.type === 'mediaType'}
						<select
							aria-label={label}
							value={condition.text}
							onchange={(event) => update(condition.key, { text: event.currentTarget.value })}
						>
							<option value="Image">{m.organize_rule_photos()}</option>
							<option value="Video">{m.organize_rule_videos()}</option>
						</select>
					{:else if condition.type === 'favorite'}
						<select
							aria-label={label}
							value={condition.value ? 'yes' : 'no'}
							onchange={(event) =>
								update(condition.key, { value: event.currentTarget.value === 'yes' })}
						>
							<option value="yes">{m.organize_rule_favorite_yes()}</option>
							<option value="no">{m.organize_rule_favorite_no()}</option>
						</select>
					{:else if condition.type === 'tag'}
						<select
							aria-label={label}
							value={condition.text}
							onchange={(event) => update(condition.key, { text: event.currentTarget.value })}
						>
							{#each structuralTags as tag (tag)}
								<option value={tag}>{tagLabels[tag]?.() ?? tag}</option>
							{/each}
						</select>
					{:else}
						<input
							type="text"
							aria-label={label}
							placeholder={condition.type === 'ocr'
								? m.search_filter_ocr_placeholder()
								: m.search_field_placeholder()}
							value={condition.text}
							onchange={(event) => update(condition.key, { text: event.currentTarget.value })}
						/>
					{/if}
					{#if (condition.type === 'person' || condition.type === 'object' || condition.type === 'scene') && (condition.type === 'person' ? condition.ids : condition.labels).length > 1}
						<select
							aria-label={m.organize_rule_match_values()}
							value={condition.match}
							onchange={(event) =>
								update(condition.key, {
									match: event.currentTarget.value as Condition['match']
								})}
						>
							<option value="any">{m.organize_rule_match_any()}</option>
							<option value="all">{m.organize_rule_match_all()}</option>
						</select>
					{/if}
				</div>
			</li>
		{/each}
	</ol>
</div>

<style>
	.editor {
		display: grid;
		gap: var(--space-3);
	}

	.head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
	}

	.head label {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
	}

	.empty {
		margin: 0;
		color: var(--color-text-muted);
	}

	ol {
		display: grid;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	li {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
	}

	li.incomplete {
		border-style: dashed;
	}

	.row-head {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.negate {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		margin-left: auto;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.control {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	.control label {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.control input[type='text'] {
		flex: 1;
		min-width: 200px;
	}
</style>
