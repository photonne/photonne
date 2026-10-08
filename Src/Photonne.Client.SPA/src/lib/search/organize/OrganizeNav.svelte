<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { page } from '$app/state';
	import { getOrganizeInboxCountOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { monthTitle } from '#lib/format.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	/** The organize sections, with the live counts from /organize/inbox/count. */

	const count = createQuery(() => getOrganizeInboxCountOptions());

	const tabs = $derived([
		{ path: '/organize', label: m.organize_tab_inbox(), count: count.data?.count },
		{ path: '/organize/rules', label: m.organize_tab_rules(), count: undefined },
		{
			path: '/organize/excluded',
			label: m.organize_tab_excluded(),
			count: count.data?.excludedCount
		}
	]);

	const range = $derived.by(() => {
		const { oldest, newest } = count.data ?? {};
		if (!oldest || !newest) return null;
		const from = monthTitle(oldest.slice(0, 7));
		const to = monthTitle(newest.slice(0, 7));
		return from === to ? from : m.organize_range({ from, to });
	});
</script>

<div class="organize-nav">
	<p class="lead">{m.organize_lead()}</p>
	{#if count.data && count.data.count > 0}
		<p class="summary">
			{range
				? `${m.organize_pending({ count: count.data.count })} · ${range}`
				: m.organize_pending({ count: count.data.count })}
		</p>
	{/if}
	<nav aria-label={m.organize_sections()}>
		{#each tabs as tab (tab.path)}
			<a
				href={appHref(tab.path)}
				aria-current={page.url.pathname === appHref(tab.path) ? 'page' : undefined}
			>
				{tab.label}
				{#if tab.count !== undefined}<span class="count">{tab.count}</span>{/if}
			</a>
		{/each}
	</nav>
</div>

<style>
	.organize-nav {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-1) var(--space-4) var(--space-3);
	}

	.lead,
	.summary {
		margin: 0;
		color: var(--color-text-muted);
	}

	.summary {
		margin-top: calc(-1 * var(--space-2));
		color: var(--color-text);
		font-weight: 600;
	}

	nav {
		display: flex;
		gap: var(--space-1);
		border-bottom: 1px solid var(--color-border);
	}

	a {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border-bottom: 2px solid transparent;
		color: var(--color-text-muted);
		text-decoration: none;
		font-weight: 600;
	}

	a:hover {
		color: var(--color-text);
	}

	a[aria-current='page'] {
		border-bottom-color: var(--color-accent);
		color: var(--color-accent);
	}

	.count {
		min-width: 1.5rem;
		padding: 0 var(--space-2);
		border-radius: 999px;
		background: var(--color-surface);
		color: var(--color-text);
		font-size: var(--font-size-xs);
		text-align: center;
	}
</style>
