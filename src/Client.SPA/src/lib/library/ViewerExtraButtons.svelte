<script lang="ts" module>
	export interface ViewerExtraAction {
		label: string;
		/** A 24×24 icon path (see icons.ts) or an Icon.svelte name. */
		icon: { path: string } | { name: IconName };
		run: () => void;
		disabled?: boolean;
	}
</script>

<script lang="ts">
	import Icon, { type IconName } from '#lib/components/Icon.svelte';

	let { actions }: { actions: ViewerExtraAction[] } = $props();
</script>

{#each actions as action (action.label)}
	<button
		type="button"
		class="icon"
		aria-label={action.label}
		title={action.label}
		disabled={action.disabled}
		onclick={action.run}
	>
		<Icon {...action.icon} />
	</button>
{/each}

<style>
	/* The look of the viewer's own top-bar buttons (AssetViewer .icon). */
	.icon {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: inherit;
		cursor: pointer;
	}

	.icon:hover {
		background: rgb(255 255 255 / 0.12);
	}

	.icon:disabled {
		opacity: 0.4;
	}
</style>
