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

{#if variant === 'icon'}
	<button
		type="button"
		class="icon"
		class:danger
		title={label}
		aria-label={label}
		{disabled}
		{onclick}
	>
		<Icon {...iconProps} />
	</button>
{:else}
	<button type="button" class="text" class:danger {disabled} {onclick}>
		<Icon {...iconProps} size={18} />
		<span>{label}</span>
	</button>
{/if}

<style>
	.icon {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.icon:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.text {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		font-size: var(--font-size-sm);
		white-space: nowrap;
		cursor: pointer;
	}

	.text:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.danger {
		color: var(--color-danger);
	}

	.text.danger {
		border-color: color-mix(in srgb, var(--color-danger) 45%, transparent);
	}

	button:disabled {
		opacity: 0.45;
		cursor: default;
	}
</style>
