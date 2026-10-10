<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';

	interface Props {
		value: string;
		/** The field's name for assistive tech, also its placeholder. */
		label: string;
		placeholder?: string;
		/** CSS width; the field never grows past its row. */
		width?: string;
	}

	let { value = $bindable(), label, placeholder = label, width = '280px' }: Props = $props();
</script>

<!-- A search box with its magnifier, on the shared input look (lib/styles/ui.css). -->
<span class="search-field" style:width>
	<Icon name="search" size={18} />
	<input type="search" bind:value aria-label={label} {placeholder} />
</span>

<style>
	.search-field {
		position: relative;
		display: inline-flex;
		max-width: 100%;
		color: var(--color-text-muted);
	}

	.search-field :global(svg) {
		position: absolute;
		top: 50%;
		left: var(--space-3);
		transform: translateY(-50%);
		pointer-events: none;
	}

	input {
		width: 100%;
		padding-left: calc(var(--space-3) + 18px + var(--space-2));
	}
</style>
