<script lang="ts">
	import { formatCount } from '#lib/format.js';
	interface Tab {
		href: string;
		label: string;
		current: boolean;
		/** A number after the label (pending suggestions, items). */
		count?: number | null;
	}

	interface Props {
		tabs: readonly Tab[];
		/** The navigation's accessible name. */
		label: string;
	}

	let { tabs, label }: Props = $props();

	let list = $state<HTMLDivElement>();
	let fadeStart = $state(false);
	let fadeEnd = $state(false);

	// Tabs that don't fit scroll sideways; a fade on each edge says there is more.
	function measure() {
		if (!list) return;
		fadeStart = list.scrollLeft > 1;
		fadeEnd = list.scrollLeft + list.clientWidth < list.scrollWidth - 1;
	}

	// The current tab is always in view, also after a narrow window opens it.
	$effect(() => {
		const current = tabs.findIndex((tab) => tab.current);
		if (!list || current < 0) return;
		list.querySelectorAll('a')[current]?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
		measure();
	});

	$effect(() => {
		if (!list) return;
		const observer = new ResizeObserver(measure);
		observer.observe(list);
		return () => observer.disconnect();
	});
</script>

<nav class="tabs" class:fade-start={fadeStart} class:fade-end={fadeEnd} aria-label={label}>
	<div class="list" bind:this={list} onscroll={measure}>
		{#each tabs as tab (tab.href)}
			<a href={tab.href} aria-current={tab.current ? 'page' : undefined}
				>{tab.label}{#if tab.count != null}<span class="count">{formatCount(tab.count)}</span
					>{/if}</a
			>
		{/each}
	</div>
</nav>

<style>
	.tabs {
		position: relative;
		border-bottom: 1px solid var(--color-border);
		background: var(--color-bg);
	}

	.list {
		display: flex;
		gap: var(--space-1);
		padding: 0 var(--page-gutter);
		overflow-x: auto;
		scrollbar-width: none;
	}

	.list::-webkit-scrollbar {
		display: none;
	}

	.tabs::before,
	.tabs::after {
		content: '';
		position: absolute;
		top: 0;
		bottom: 1px;
		width: 48px;
		pointer-events: none;
		opacity: 0;
		transition: opacity var(--duration-fast);
	}

	.tabs::before {
		left: 0;
		background: linear-gradient(to right, var(--color-bg), transparent);
	}

	.tabs::after {
		right: 0;
		background: linear-gradient(to left, var(--color-bg), transparent);
	}

	.fade-start::before,
	.fade-end::after {
		opacity: 1;
	}

	a {
		position: relative;
		display: inline-flex;
		align-items: center;
		min-height: 44px;
		padding: 0 var(--space-3);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		font-weight: 500;
		text-decoration: none;
		white-space: nowrap;
	}

	a:hover {
		color: var(--color-text);
	}

	.count {
		margin-left: var(--space-2);
		min-width: 20px;
		padding: 0 6px;
		border-radius: 999px;
		background: var(--color-surface);
		font-size: var(--font-size-2xs);
		font-weight: 600;
		line-height: 18px;
		text-align: center;
	}

	a[aria-current='page'] .count {
		background: var(--color-accent-soft);
		color: var(--color-accent);
	}

	/* Inside the scroll box: drawn inwards, or the box would clip it. */
	a:focus-visible {
		outline-offset: -2px;
		border-radius: var(--radius-sm);
	}

	a[aria-current='page'] {
		color: var(--color-text);
		font-weight: 600;
	}

	a[aria-current='page']::after {
		content: '';
		position: absolute;
		left: var(--space-3);
		right: var(--space-3);
		bottom: -1px;
		height: 2px;
		border-radius: 2px 2px 0 0;
		background: var(--color-accent);
	}
</style>
