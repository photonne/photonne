<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon, { type IconName } from '../Icon.svelte';

	interface Props {
		title: string;
		/** What to do about it, in a sentence. */
		hint?: string | null;
		icon?: IconName;
		/** An SVG path, for icons outside the shared set. */
		iconPath?: string;
		/** The way out (upload, create…): a .btn link or button. */
		action?: Snippet;
		/** Less room, inside a panel or a section. */
		compact?: boolean;
	}

	let { title, hint = null, icon, iconPath, action, compact = false }: Props = $props();
</script>

<div class="empty" class:compact>
	{#if icon || iconPath}
		<span class="mark" aria-hidden="true">
			{#if iconPath}<Icon path={iconPath} size={compact ? 24 : 32} />{:else if icon}<Icon
					name={icon}
					size={compact ? 24 : 32}
				/>{/if}
		</span>
	{/if}
	<p class="title">{title}</p>
	{#if hint}<p class="hint">{hint}</p>{/if}
	{#if action}<div class="action">{@render action()}</div>{/if}
</div>

<style>
	.empty {
		display: grid;
		justify-items: center;
		align-content: center;
		gap: var(--space-2);
		min-height: 320px;
		padding: var(--space-8) var(--page-gutter);
		text-align: center;
	}

	.compact {
		min-height: 0;
		padding: var(--space-6) var(--space-4);
	}

	.mark {
		display: grid;
		place-items: center;
		width: 72px;
		height: 72px;
		margin-bottom: var(--space-2);
		border-radius: 50%;
		background: var(--color-brand-tile);
		color: var(--color-brand);
	}

	.compact .mark {
		width: 52px;
		height: 52px;
	}

	.title {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	.compact .title {
		font-size: var(--font-size-md);
	}

	.hint {
		margin: 0;
		max-width: 46ch;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.action {
		margin-top: var(--space-3);
	}
</style>
