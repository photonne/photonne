<script lang="ts">
	import Icon, { type IconName } from '#lib/components/Icon.svelte';

	interface Props {
		label: string;
		/** An Icon.svelte name or a 24×24 path from icons.ts. */
		icon: IconName | { path: string };
		/** `icon`: a round icon-only button (selection bars); `text`: icon and label (page toolbars). */
		variant?: 'icon' | 'text';
		danger?: boolean;
		disabled?: boolean;
		onclick: () => void;
	}

	let {
		label,
		icon,
		variant = 'text',
		danger = false,
		disabled = false,
		onclick
	}: Props = $props();

	const iconProps = $derived(typeof icon === 'string' ? { name: icon } : icon);
</script>

<!-- The shared .btn / .icon-btn (lib/styles/ui.css). -->
{#if variant === 'icon'}
	<button
		type="button"
		class="icon-btn"
		class:danger
		title={label}
		aria-label={label}
		{disabled}
		{onclick}
	>
		<Icon {...iconProps} />
	</button>
{:else}
	<button type="button" class="btn" class:danger {disabled} {onclick}>
		<Icon {...iconProps} size={18} />
		<span>{label}</span>
	</button>
{/if}

<style>
	.icon-btn.danger:not(:disabled) {
		color: var(--color-danger);
	}
</style>
