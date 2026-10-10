<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon, { type IconName } from '#lib/components/Icon.svelte';

	interface Props {
		id: string;
		icon: { name?: IconName; path?: string };
		title: string;
		description: string;
		/** Deletes something: the icon says so before the button does. */
		danger?: boolean;
		active?: boolean;
		children?: Snippet;
		actions?: Snippet;
	}

	let {
		id,
		icon,
		title,
		description,
		danger = false,
		active = false,
		children,
		actions
	}: Props = $props();
</script>

<li class="row" class:active aria-labelledby="{id}-title" aria-describedby="{id}-desc">
	<span class="icon" class:danger><Icon name={icon.name} path={icon.path} size={20} /></span>
	<div class="main">
		<h3 id="{id}-title">{title}</h3>
		<p id="{id}-desc" class="desc">{description}</p>
		{#if children}<div class="body">{@render children()}</div>{/if}
	</div>
	{#if actions}<div class="actions">{@render actions()}</div>{/if}
</li>

<style>
	.row {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr) auto;
		align-items: start;
		gap: var(--space-3) var(--space-4);
		padding: var(--space-4);
		border-top: 1px solid var(--color-border);
	}

	.row:first-child {
		border-top: none;
	}

	.row.active {
		background: color-mix(in srgb, var(--color-accent) 4%, transparent);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border-radius: 50%;
		background: color-mix(in srgb, var(--color-accent) 12%, transparent);
		color: var(--color-accent);
	}

	.icon.danger {
		background: color-mix(in srgb, var(--color-danger) 12%, transparent);
		color: var(--color-danger);
	}

	h3 {
		margin: 0;
		font-size: var(--font-size-md);
	}

	.desc {
		margin: 2px 0 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.body {
		display: grid;
		gap: var(--space-2);
		margin-top: var(--space-2);
	}

	.body:empty {
		display: none;
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		justify-content: flex-end;
		gap: var(--space-2);
	}

	@media (max-width: 720px) {
		.row {
			grid-template-columns: auto minmax(0, 1fr);
		}

		.actions {
			grid-column: 2;
			justify-content: flex-start;
		}
	}
</style>
