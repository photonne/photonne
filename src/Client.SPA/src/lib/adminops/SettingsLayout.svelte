<script lang="ts">
	import type { Snippet } from 'svelte';
	import '#lib/admin/admin.css';
	import Icon from '#lib/components/Icon.svelte';
	import Tabs from '#lib/components/ui/Tabs.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import './adminops.css';
	import { sectionGroups, sections } from './settings-sections.js';

	// A component the section page wraps itself in, not a +layout.svelte: an
	// extra layout level makes leaving the settings for another admin tab
	// crash SvelteKit's root (it reads `.data` of the node that just went away)
	// and show a 500.
	let { current, children }: { current: string; children: Snippet } = $props();

	// One section navigation: a grouped list at the side on a wide screen,
	// the same sections as tabs on a narrow one (only one is ever shown).
	const tabs = $derived(
		sections.map((section) => ({
			href: appHref(`/admin/settings/${section.id}`),
			label: section.title(),
			current: current === section.id
		}))
	);
</script>

<div class="admin-page settings">
	<nav class="side" aria-label={m.ops_set_nav()}>
		{#each sectionGroups as group (group.title())}
			<h2>{group.title()}</h2>
			<ul>
				{#each group.sections as section (section.id)}
					<li>
						<a
							href={appHref(`/admin/settings/${section.id}`)}
							aria-current={current === section.id ? 'page' : undefined}
						>
							<Icon name={section.icon.name} path={section.icon.path} size={18} />
							{section.title()}
						</a>
					</li>
				{/each}
			</ul>
		{/each}
	</nav>
	<div class="tabs"><Tabs {tabs} label={m.ops_set_nav()} /></div>
	<div class="content">{@render children()}</div>
</div>

<style>
	.settings {
		/* Room the section tabs take above the editor (none beside a list). */
		--settings-nav-height: 0px;
		display: grid;
		grid-template-columns: 232px minmax(0, 1fr);
		align-items: start;
	}

	.side {
		position: sticky;
		top: var(--admin-nav-height);
		max-height: calc(100dvh - var(--chrome-top) - var(--admin-nav-height));
		overflow-y: auto;
		padding: var(--space-6) var(--space-3) var(--space-6) var(--page-gutter);
	}

	h2 {
		margin: var(--space-4) 0 var(--space-1);
		padding: 0 var(--space-3);
		color: var(--color-text-muted);
		font-size: var(--font-size-2xs);
		font-weight: 700;
		letter-spacing: 0.06em;
		text-transform: uppercase;
	}

	h2:first-child {
		margin-top: 0;
	}

	ul {
		display: grid;
		gap: 2px;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	a {
		position: relative;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-height: var(--control-h);
		padding: var(--space-1) var(--space-3);
		border-radius: var(--radius-control);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		line-height: 1.3;
		text-decoration: none;
	}

	a :global(svg) {
		flex: none;
	}

	a:hover {
		background: var(--color-hover);
		color: var(--color-text);
	}

	/* The current section, as in the app's navigation: a neutral pill. */
	a[aria-current='page'] {
		background: var(--color-active-pill);
		color: var(--color-text);
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

	@media (max-width: 1200px) {
		.settings {
			--settings-nav-height: 45px;
			grid-template-columns: minmax(0, 1fr);
		}

		.side {
			display: none;
		}

		.tabs {
			display: block;
			position: sticky;
			top: var(--admin-nav-height);
			z-index: 2;
		}
	}
</style>
