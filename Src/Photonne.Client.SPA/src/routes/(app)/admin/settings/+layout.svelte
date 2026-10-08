<script lang="ts">
	import { page } from '$app/state';
	import '#lib/adminops/adminops.css';
	import { sectionGroups } from '#lib/adminops/settings-sections.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const current = $derived(page.params.section);
</script>

<div class="settings">
	<nav aria-label={m.ops_set_nav()}>
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
	<div class="content">{@render children()}</div>
</div>

<style>
	.settings {
		display: grid;
		grid-template-columns: 240px minmax(0, 1fr);
		align-items: start;
	}

	nav {
		position: sticky;
		top: 57px;
		max-height: calc(100vh - var(--header-height) - 57px);
		overflow-y: auto;
		padding: var(--space-4) var(--space-2) var(--space-6) var(--space-4);
		border-right: 1px solid var(--color-border);
	}

	h2 {
		margin: var(--space-4) 0 var(--space-1);
		padding: 0 var(--space-3);
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		font-weight: 600;
		letter-spacing: 0.04em;
		text-transform: uppercase;
	}

	h2:first-child {
		margin-top: 0;
	}

	ul {
		display: grid;
		gap: 1px;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	a {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		color: inherit;
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	a:hover {
		background: var(--color-surface);
	}

	a[aria-current='page'] {
		background: var(--color-surface);
		color: var(--color-accent);
		font-weight: 600;
	}

	@media (max-width: 960px) {
		.settings {
			grid-template-columns: 1fr;
		}

		nav {
			position: static;
			max-height: none;
			display: flex;
			flex-wrap: wrap;
			gap: var(--space-1) var(--space-4);
			padding: var(--space-3) var(--space-4);
			border-right: none;
			border-bottom: 1px solid var(--color-border);
		}

		nav h2 {
			display: none;
		}

		nav ul {
			display: flex;
			flex-wrap: wrap;
			gap: var(--space-1);
		}

		a {
			padding: var(--space-1) var(--space-2);
		}
	}
</style>
