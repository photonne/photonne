<script lang="ts" module>
	export interface PickerOption {
		value: string;
		label: string;
		/** Second line (a folder's path, a label's photo count…). */
		hint?: string;
	}
</script>

<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { fold } from './album-list.js';

	interface Props {
		label: string;
		options: readonly PickerOption[];
		selected: string[];
		onchange: (selected: string[]) => void;
		/** Shown for selected values the options don't include (deleted person…). */
		unknownLabel?: (value: string) => string;
		loading?: boolean;
	}

	let { label, options, selected, onchange, unknownLabel, loading = false }: Props = $props();

	const LIMIT = 100;
	const id = $props.id();
	let filter = $state('');

	const byValue = $derived(new Map(options.map((option) => [option.value, option])));
	const matching = $derived.by(() => {
		const needle = fold(filter.trim());
		return needle
			? options.filter((option) => fold(`${option.label} ${option.hint ?? ''}`).includes(needle))
			: options;
	});

	function toggle(value: string, on: boolean) {
		onchange(on ? [...selected, value] : selected.filter((v) => v !== value));
	}
</script>

<fieldset class="picker">
	<legend class="visually-hidden">{label}</legend>
	{#if selected.length}
		<ul class="chips" aria-label={m.albums_picker_selected()}>
			{#each selected as value (value)}
				{@const name = byValue.get(value)?.label ?? unknownLabel?.(value) ?? value}
				<li class="picked">
					{name}
					<button
						type="button"
						aria-label={m.albums_picker_remove({ name })}
						onclick={() => toggle(value, false)}
					>
						<Icon name="close" size={14} />
					</button>
				</li>
			{/each}
		</ul>
	{/if}
	<input
		type="search"
		aria-label={m.albums_picker_filter({ label })}
		placeholder={m.albums_picker_filter({ label })}
		aria-controls="{id}-options"
		bind:value={filter}
	/>
	<ul id="{id}-options" class="options">
		{#each matching.slice(0, LIMIT) as option (option.value)}
			<li>
				<label>
					<input
						type="checkbox"
						checked={selected.includes(option.value)}
						onchange={(event) => toggle(option.value, event.currentTarget.checked)}
					/>
					<span class="text">
						<span>{option.label}</span>
						{#if option.hint}<span class="hint">{option.hint}</span>{/if}
					</span>
				</label>
			</li>
		{:else}
			<li class="empty">{loading ? m.session_restoring() : m.albums_picker_none()}</li>
		{/each}
		{#if matching.length > LIMIT}
			<li class="empty">{m.albums_picker_more({ count: matching.length - LIMIT })}</li>
		{/if}
	</ul>
</fieldset>

<style>
	.picker {
		display: grid;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		border: 0;
		min-width: 0;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.picked {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		min-height: 28px;
		padding: 0 2px 0 var(--space-3);
		border: 1px solid var(--color-accent);
		border-radius: 999px;
		background: var(--color-accent-soft);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.picked button {
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.picked button:hover {
		background: var(--color-hover);
	}

	input[type='search'] {
		width: 100%;
	}

	.options {
		max-height: 168px;
		overflow-y: auto;
		margin: 0;
		padding: 0;
		list-style: none;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
	}

	.options label {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-2);
		cursor: pointer;
	}

	.options label:hover {
		background: var(--color-hover);
	}

	.text {
		display: grid;
		min-width: 0;
	}

	.hint,
	.empty {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.empty {
		padding: var(--space-2);
	}
</style>
