<script lang="ts">
	import '#lib/account/settings/settings.css';
	import { page } from '$app/state';
	import {
		ICON_AUTO_AWESOME,
		ICON_FOLDER_SHARED,
		ICON_PALETTE,
		ICON_STORAGE
	} from '#lib/account/icons.js';
	import Icon, { type IconName } from '#lib/components/Icon.svelte';
	import Tabs from '#lib/components/ui/Tabs.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const sections: { path: string; label: () => string; icon?: IconName; iconPath?: string }[] = [
		{ path: '/settings/profile', label: m.settings_profile, icon: 'person' },
		{ path: '/settings/security', label: m.settings_security, icon: 'lock' },
		{ path: '/settings/appearance', label: m.settings_appearance, iconPath: ICON_PALETTE },
		{ path: '/settings/storage', label: m.settings_storage, iconPath: ICON_STORAGE },
		{ path: '/settings/shared-folders', label: m.settings_discovery, iconPath: ICON_FOLDER_SHARED },
		{ path: '/settings/analysis', label: m.settings_analysis, iconPath: ICON_AUTO_AWESOME }
	];

	const tabs = $derived(
		sections.map((section) => {
			const href = appHref(section.path);
			return { href, label: section.label(), current: page.url.pathname === href };
		})
	);
</script>

<div class="layout">
	<!-- Wide windows: a side list. Narrow ones: tabs above the page (one shows at a time). -->
	<nav class="side" aria-label={m.nav_settings()}>
		<h2>{m.nav_settings()}</h2>
		<ul>
			{#each sections as section, i (section.path)}
				<li>
					<a href={tabs[i].href} aria-current={tabs[i].current ? 'page' : undefined}>
						<Icon name={section.icon} path={section.iconPath} size={18} />
						{section.label()}
					</a>
				</li>
			{/each}
		</ul>
	</nav>
	<div class="tabs"><Tabs {tabs} label={m.nav_settings()} /></div>
	<div class="content">
		{@render children()}
	</div>
</div>

<style>
	.layout {
		display: grid;
		grid-template-columns: 232px minmax(0, 1fr);
		min-height: 100%;
	}

	.side {
		padding: var(--space-6) var(--space-3) var(--space-8) var(--page-gutter);
		border-right: 1px solid var(--color-border);
	}

	.side h2 {
		margin: var(--space-1) var(--space-3) var(--space-4);
		font-size: var(--font-size-lg);
		font-weight: 650;
		line-height: 1.2;
	}

	ul {
		position: sticky;
		top: var(--space-4);
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
	}

	a {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: var(--control-h);
		padding: 0 var(--space-3);
		border-radius: var(--radius-control);
		color: var(--color-text);
		font-size: var(--font-size-sm);
		font-weight: 500;
		text-decoration: none;
		white-space: nowrap;
	}

	a :global(svg) {
		flex: none;
		color: var(--color-text-muted);
	}

	a:hover {
		background: var(--color-hover);
	}

	a[aria-current='page'] {
		background: var(--color-accent-soft);
		font-weight: 600;
	}

	a[aria-current='page'] :global(svg) {
		color: var(--color-accent);
	}

	.tabs {
		display: none;
	}

	.content {
		min-width: 0;
	}

	/* Narrower windows: the shared tabs, which scroll when they don't fit. */
	@media (max-width: 1180px) {
		.layout {
			grid-template-columns: minmax(0, 1fr);
			grid-template-rows: auto 1fr;
		}

		.side {
			display: none;
		}

		.tabs {
			display: block;
		}
	}
</style>
