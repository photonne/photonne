<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';

	/**
	 * The heading of a section inside a page (a row of Explorar, a block of
	 * Recuerdos, Buscar's suggestions…): one size and weight everywhere, with
	 * "Ver todo" right after the title instead of at the far edge.
	 */
	interface Props {
		id?: string;
		title: string;
		/** A short fact after the title ("12"). */
		count?: string | number | null;
		/** "See all": where the full list lives. */
		more?: { href: string; label?: string };
	}

	let { id, title, count = null, more }: Props = $props();
</script>

<div class="section-title">
	<h2 {id}>{title}</h2>
	{#if count !== null && count !== undefined}<span class="count">{count}</span>{/if}
	{#if more}
		<a class="more" href={more.href}>
			{more.label ?? m.explore_see_all()}
			<Icon name="chevronRight" size={16} />
		</a>
	{/if}
</div>

<style>
	.section-title {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-1) var(--space-2);
		min-height: var(--control-h-sm);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-md);
		font-weight: 650;
		line-height: 1.3;
	}

	.count {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.more {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		min-height: var(--control-h-sm);
		padding: 0 var(--space-1) 0 var(--space-2);
		border-radius: var(--radius-control);
		color: var(--color-accent);
		font-size: var(--font-size-sm);
		font-weight: 600;
		text-decoration: none;
	}

	.more:hover {
		background: var(--color-accent-soft);
	}
</style>
