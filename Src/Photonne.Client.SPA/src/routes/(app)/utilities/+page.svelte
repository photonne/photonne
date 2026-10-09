<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getUtilitiesSummaryOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { formatBytes } from '#lib/format.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { duplicatesPath, filePath, folderOpenPath, storagePath } from '#lib/search/icons.js';

	// Live figures; without them (an old server, a failure) the cards still
	// work with their plain descriptions.
	const summary = createQuery(() => getUtilitiesSummaryOptions());

	const cards = $derived([
		{
			path: '/utilities/duplicates',
			icon: duplicatesPath,
			title: m.utilities_duplicates(),
			description: m.utilities_duplicates_description(),
			figure: summary.data
				? summary.data.duplicateGroups
					? m.utilities_duplicates_figure({
							count: summary.data.duplicateGroups,
							size: formatBytes(summary.data.duplicateRecoverableBytes)
						})
					: m.utilities_nothing()
				: null,
			attention: (summary.data?.duplicateGroups ?? 0) > 0
		},
		{
			path: '/utilities/large-files',
			icon: storagePath,
			title: m.utilities_large_files(),
			description: m.utilities_large_files_description(),
			figure: summary.data?.largeFilesCount
				? m.utilities_large_files_figure({
						count: summary.data.largeFilesCount,
						size: formatBytes(summary.data.largeFilesBytes)
					})
				: null,
			attention: false
		},
		{
			path: '/utilities/locations',
			icon: folderOpenPath,
			title: m.utilities_locations(),
			description: m.utilities_locations_description(),
			figure: null,
			attention: false
		},
		{
			path: '/utilities/unsupported',
			icon: filePath,
			title: m.utilities_unsupported(),
			description: m.utilities_unsupported_description(),
			figure: summary.data
				? summary.data.unsupportedCount
					? m.utilities_unsupported_figure({ count: summary.data.unsupportedCount })
					: m.utilities_nothing()
				: null,
			attention: (summary.data?.unsupportedCount ?? 0) > 0
		}
	]);
</script>

<svelte:head>
	<title>{m.nav_utilities()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader title={m.nav_utilities()} subtitle={m.utilities_lead()} />

	<ul>
		{#each cards as card (card.path)}
			<li>
				<a href={appHref(card.path)}>
					<span class="icon" class:attention={card.attention}
						><Icon path={card.icon} size={24} /></span
					>
					<span class="text">
						<span class="title">{card.title}</span>
						<span class="description">{card.description}</span>
						{#if card.figure}<span class="figure">{card.figure}</span>{/if}
					</span>
					<Icon name="chevronRight" />
				</a>
			</li>
		{/each}
	</ul>
</div>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	ul {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
		gap: var(--space-3);
		max-width: 1100px;
		margin: 0;
		padding: var(--space-2) var(--page-gutter);
		list-style: none;
	}

	a {
		display: flex;
		align-items: center;
		gap: var(--space-4);
		height: 100%;
		padding: var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
		color: inherit;
		text-decoration: none;
		transition: border-color var(--duration-fast);
	}

	a:hover {
		border-color: var(--color-border-strong);
		background: var(--color-surface);
	}

	a > :global(svg) {
		flex: none;
		color: var(--color-text-muted);
	}

	.icon {
		display: grid;
		place-items: center;
		flex: none;
		width: 48px;
		height: 48px;
		border-radius: var(--radius-md);
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.icon.attention {
		background: var(--color-accent-soft);
		color: var(--color-accent);
	}

	.text {
		flex: 1;
		display: grid;
		gap: 2px;
	}

	.title {
		font-weight: 600;
	}

	.description {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.figure {
		margin-top: var(--space-1);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}
</style>
