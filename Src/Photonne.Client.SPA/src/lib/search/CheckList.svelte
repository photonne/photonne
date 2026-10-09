<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import { matches } from './text.js';

	export interface CheckOption {
		value: string;
		label: string;
		/** Small text after the label (a count). */
		hint?: string;
	}

	interface Props {
		/** Accessible name of the list. */
		label: string;
		options: readonly CheckOption[];
		selected: readonly string[];
		ontoggle: (value: string) => void;
		/** Typing filters the options here, or asks the host (a server search). */
		onsearch?: (text: string) => void;
		loading?: boolean;
	}

	let { label, options, selected, ontoggle, onsearch, loading = false }: Props = $props();

	let text = $state('');

	// Chosen values stay on top so they can be unticked without scrolling.
	const shown = $derived.by(() => {
		const visible = onsearch ? options : options.filter((option) => matches(option.label, text));
		return [
			...visible.filter((option) => selected.includes(option.value)),
			...visible.filter((option) => !selected.includes(option.value))
		];
	});
</script>

<div class="checklist">
	<input
		type="search"
		aria-label={m.search_filter_options({ list: label })}
		placeholder={m.search_filter_placeholder()}
		bind:value={text}
		oninput={() => onsearch?.(text.trim())}
	/>
	{#if loading}
		<p class="note" role="status">{m.search_loading()}</p>
	{:else if shown.length === 0}
		<p class="note">{m.search_filter_nothing()}</p>
	{:else}
		<ul aria-label={label}>
			{#each shown as option (option.value)}
				<li>
					<label>
						<input
							type="checkbox"
							checked={selected.includes(option.value)}
							onchange={() => ontoggle(option.value)}
						/>
						<span class="name">{option.label}</span>
						{#if option.hint}<span class="hint">{option.hint}</span>{/if}
					</label>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.checklist {
		display: grid;
		gap: var(--space-2);
	}

	input[type='search'] {
		width: 100%;
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		max-height: 280px;
		overflow-y: auto;
	}

	label {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-2);
		border-radius: var(--radius-sm);
		cursor: pointer;
	}

	label:hover {
		background: var(--color-hover);
	}

	.name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.hint,
	.note {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.note {
		margin: 0;
		padding: var(--space-2);
	}
</style>
