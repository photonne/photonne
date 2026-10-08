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
</script>

<div class="layout">
	<nav aria-label={m.nav_settings()}>
		<h2>{m.nav_settings()}</h2>
		<ul>
			{#each sections as section (section.path)}
				{@const href = appHref(section.path)}
				<li>
					<a {href} aria-current={page.url.pathname === href ? 'page' : undefined}>
						<Icon name={section.icon} path={section.iconPath} size={18} />
						{section.label()}
					</a>
				</li>
			{/each}
		</ul>
	</nav>
	<div class="content">
		{@render children()}
	</div>
</div>

<style>
	.layout {
		display: grid;
		grid-template-columns: 220px minmax(0, 1fr);
		gap: var(--space-8);
		padding: var(--space-4) var(--space-6) var(--space-8);
	}

	nav h2 {
		margin: var(--space-2) var(--space-3) var(--space-2);
		font-size: var(--font-size-xs);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-muted);
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
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		color: inherit;
		text-decoration: none;
		white-space: nowrap;
	}

	a:hover {
		background: var(--color-surface);
	}

	a[aria-current='page'] {
		background: var(--color-surface);
		color: var(--color-accent);
		font-weight: 600;
	}

	.content {
		min-width: 0;
	}

	/* A narrow window: the sections become a row of tabs above the page. */
	@media (max-width: 1040px) {
		.layout {
			grid-template-columns: minmax(0, 1fr);
			gap: var(--space-4);
		}

		nav h2 {
			display: none;
		}

		ul {
			position: static;
			display: flex;
			overflow-x: auto;
			border-bottom: 1px solid var(--color-border);
		}

		a {
			border-radius: var(--radius-sm) var(--radius-sm) 0 0;
		}

		a[aria-current='page'] {
			box-shadow: inset 0 -2px 0 var(--color-accent);
		}
	}
</style>
