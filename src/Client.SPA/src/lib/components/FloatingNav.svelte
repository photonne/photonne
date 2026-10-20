<script lang="ts">
	import { page } from '$app/state';
	import { appHref as href } from '#lib/navigation/href.js';
	import { chrome } from '#lib/navigation/chrome.svelte.js';
	import { tabFor, tabs } from '#lib/navigation/tabs.js';
	import { m } from '#lib/paraglide/messages.js';
	import Icon from './Icon.svelte';

	/** Unread notifications, shown on «Más» as in the native app. */
	let { unread = 0 }: { unread?: number } = $props();

	const current = $derived(tabFor(page.url.pathname, href));
	// A selection's actions take the capsule's place; scrolling down hides it.
	const away = $derived(chrome.selecting > 0 || chrome.immersive);
</script>

<!-- The phone's navigation: a glass capsule floating over the page's
     bottom edge, as wide as its three tabs (the native MainNavigationBar). -->
<nav class="floating-nav glass" class:away aria-label={m.nav_tabs()} inert={away}>
	{#each tabs as tab (tab.id)}
		{@const active = tab.id === current}
		<a
			href={href(tab.path)}
			aria-current={active ? (page.url.pathname === href(tab.path) ? 'page' : 'true') : undefined}
		>
			<span class="glyph">
				<Icon name={active ? tab.activeIcon : tab.icon} size={22} />
				{#if tab.id === 'more' && unread > 0}
					<span class="badge" aria-hidden="true">{unread > 99 ? '99+' : unread}</span>
				{/if}
			</span>
			<span class="label">{tab.label()}</span>
			{#if tab.id === 'more' && unread > 0}
				<span class="visually-hidden">{m.nav_notifications_unread({ count: unread })}</span>
			{/if}
		</a>
	{/each}
</nav>

<style>
	.floating-nav {
		position: fixed;
		inset: auto 0 calc(env(safe-area-inset-bottom, 0px) + 8px);
		width: max-content;
		max-width: calc(100vw - 2 * var(--float-margin));
		margin-inline: auto;
		z-index: 30;
		display: none;
		grid-auto-flow: column;
		/* Every tab as wide as the widest, as EqualWidthRow does. */
		grid-auto-columns: 1fr;
		gap: 4px;
		height: var(--nav-pill-h);
		padding: 0 6px;
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-nav);
		transition:
			translate var(--duration-chrome) var(--ease-standard),
			opacity var(--duration-chrome);
	}

	/* Slides down out of sight, its shadow too. */
	.floating-nav.away {
		translate: 0 calc(100% + 24px);
		opacity: 0;
	}

	@media (max-width: 700px) {
		.floating-nav {
			display: grid;
		}
	}

	a {
		display: grid;
		place-items: center;
		align-content: center;
		gap: 2px;
		min-width: 64px;
		margin: 6px 0;
		padding: 0 14px;
		border-radius: var(--radius-pill);
		color: inherit;
		text-decoration: none;
		transition: background var(--duration-normal) var(--ease-standard);
	}

	a[aria-current] {
		background: var(--color-active-pill);
	}

	a[aria-current] .glyph {
		color: var(--color-accent);
	}

	.glyph {
		position: relative;
		display: grid;
	}

	.label {
		font-size: var(--font-size-2xs);
		font-weight: 600;
		white-space: nowrap;
	}

	a[aria-current] .label {
		font-weight: 700;
	}

	.badge {
		position: absolute;
		top: -4px;
		left: 14px;
		min-width: 16px;
		padding: 0 4px;
		border-radius: 8px;
		background: var(--color-badge);
		color: var(--color-badge-text);
		box-shadow: 0 0 0 2px var(--chrome-base);
		font-size: 10px;
		font-weight: 700;
		line-height: 16px;
		text-align: center;
	}
</style>
