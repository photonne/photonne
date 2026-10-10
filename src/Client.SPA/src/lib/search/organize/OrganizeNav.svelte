<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { page } from '$app/state';
	import { getOrganizeInboxCountOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Tabs from '#lib/components/ui/Tabs.svelte';
	import { monthTitle, formatCount } from '#lib/format.js';
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

<div class="intro">
	<p class="lead">{m.organize_lead()}</p>
	{#if count.data && count.data.count > 0}
		<p class="summary">
			{range
				? `${m.organize_pending({ count: count.data.count })} · ${range}`
				: m.organize_pending({ count: count.data.count })}
		</p>
	{/if}
</div>
<Tabs
	label={m.organize_sections()}
	tabs={tabs.map((tab) => ({
		href: appHref(tab.path),
		// The count reads as part of the tab's name ("Bandeja 32").
		label: tab.count !== undefined ? `${tab.label} ${formatCount(tab.count)}` : tab.label,
		current: page.url.pathname === appHref(tab.path)
	}))}
/>

<style>
	.intro {
		display: grid;
		gap: var(--space-1);
		padding: 0 var(--page-gutter) var(--space-3);
	}

	.lead,
	.summary {
		max-width: 80ch;
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.summary {
		color: var(--color-text);
		font-weight: 600;
	}
</style>
