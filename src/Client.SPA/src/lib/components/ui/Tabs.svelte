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
	<!-- A glass capsule, as the native app's segmented chrome; the glass sits on
	     this wrapper so it stays put while the tabs scroll sideways inside. -->
	<div class="pill glass">
		<div class="list" bind:this={list} onscroll={measure}>
			{#each tabs as tab (tab.href)}
				<a href={tab.href} aria-current={tab.current ? 'page' : undefined}
					>{tab.label}{#if tab.count != null}<span class="count">{formatCount(tab.count)}</span
						>{/if}</a
				>
			{/each}
		</div>
	</div>
</nav>

<style>
	/* 40 px of capsule in a 45 px row: pages pin their sticky bars right
	   below it (--admin-nav-height). */
	.tabs {
		padding: 2px var(--page-gutter) 3px;
	}

	.pill {
		width: max-content;
		max-width: 100%;
		overflow: hidden;
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-bar);
	}

	.list {
		display: flex;
		gap: 2px;
		padding: 3px;
		overflow-x: auto;
		scrollbar-width: none;
	}

	.list::-webkit-scrollbar {
		display: none;
	}

	/* Tabs that don't fit fade out at the edge with more to scroll. */
	.fade-start .list {
		mask-image: linear-gradient(to right, transparent, #000 40px);
	}

	.fade-end .list {
		mask-image: linear-gradient(to left, transparent, #000 40px);
	}

	.fade-start.fade-end .list {
		mask-image: linear-gradient(
			to right,
			transparent,
			#000 40px,
			#000 calc(100% - 40px),
			transparent
		);
	}

	a {
		position: relative;
		display: inline-flex;
		align-items: center;
		flex: none;
		min-height: 32px;
		padding: 0 var(--space-4);
		border-radius: var(--radius-pill);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		font-weight: 500;
		text-decoration: none;
		white-space: nowrap;
		transition:
			background var(--duration-normal) var(--ease-standard),
			color var(--duration-fast);
	}

	a:hover {
		background: var(--color-hover);
		color: var(--color-text);
	}

	.count {
		margin-left: var(--space-2);
		min-width: 20px;
		padding: 0 6px;
		border-radius: 999px;
		background: var(--color-hover);
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
	}

	/* The current tab: the neutral pill of the native navigation. */
	a[aria-current='page'] {
		background: var(--color-active-pill);
		color: var(--color-text);
		font-weight: 600;
	}
</style>
