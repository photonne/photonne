<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import type { ListView } from './album-list.js';

	let { view = $bindable() }: { view: ListView } = $props();

	const name = $props.id();
	// Material "view_module" and "view_list" (Apache 2.0).
	const options: { key: ListView; label: () => string; path: string }[] = [
		{
			key: 'grid',
			label: m.albums_view_grid,
			path: 'M4 11h5V5H4v6zm0 7h5v-6H4v6zm6 0h5v-6h-5v6zm6 0h5v-6h-5v6zm-6-7h5V5h-5v6zm6-6v6h5V5h-5z'
		},
		{
			key: 'list',
			label: m.albums_view_list,
			path: 'M4 14h4v-4H4v4zm0 5h4v-4H4v4zM4 9h4V5H4v4zm5 5h12v-4H9v4zm0 5h12v-4H9v4zM9 5v4h12V5H9z'
		}
	];
</script>

<div class="toggle" role="radiogroup" aria-label={m.albums_view()}>
	{#each options as option (option.key)}
		<label class:on={view === option.key} title={option.label()}>
			<input type="radio" {name} value={option.key} bind:group={view} />
			<Icon path={option.path} size={18} />
			<span class="visually-hidden">{option.label()}</span>
		</label>
	{/each}
</div>

<style>
	.toggle {
		display: inline-flex;
		padding: 2px;
		border-radius: var(--radius-sm);
		background: var(--color-surface);
	}

	label {
		position: relative;
		display: grid;
		place-items: center;
		width: 34px;
		height: 32px;
		border-radius: calc(var(--radius-sm) - 2px);
		color: var(--color-text-muted);
		cursor: pointer;
	}

	label.on {
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
		color: var(--color-text);
	}

	label:has(input:focus-visible) {
		outline: 2px solid var(--color-focus);
	}

	/* The radio covers its label: invisible, but it is what gets clicked. */
	input {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		margin: 0;
		opacity: 0;
		cursor: pointer;
	}
</style>
