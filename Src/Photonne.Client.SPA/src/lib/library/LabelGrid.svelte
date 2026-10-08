<script lang="ts">
	import { createQuery, keepPreviousData } from '@tanstack/svelte-query';
	import {
		listObjectLabelsOptions,
		listSceneLabelsOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { labelHref, type LabelKind } from './explore-links.js';
	import { isLabelSort, sortLabels, type LabelSort } from './labels.js';
	import LabelTile from './LabelTile.svelte';
	import PageCrumbs from './PageCrumbs.svelte';
	import PageHeader from './PageHeader.svelte';

	/** Every scene or object label, with a search (on the server) and a sort. */
	let { kind }: { kind: LabelKind } = $props();

	const LIMIT = 200;
	const SORT_KEY = $derived(`photonne.explore.${kind}.sort`);

	let search = $state('');
	let query = $state('');
	let sort = $state<LabelSort>('count');

	// Remembered per viewer: how they like to browse each grid.
	$effect.pre(() => {
		try {
			const saved = localStorage.getItem(SORT_KEY);
			if (isLabelSort(saved)) sort = saved;
		} catch {
			// Not remembered; counts first.
		}
	});

	function setSort(value: LabelSort) {
		sort = value;
		try {
			localStorage.setItem(SORT_KEY, value);
		} catch {
			// Just not remembered.
		}
	}

	// The server filters (`q`), so a search reaches past the first 200.
	$effect(() => {
		const text = search.trim();
		const timer = setTimeout(() => (query = text), 250);
		return () => clearTimeout(timer);
	});

	const labels = createQuery(() => {
		const options = { query: { limit: LIMIT, q: query || undefined } };
		return {
			...(kind === 'scenes' ? listSceneLabelsOptions(options) : listObjectLabelsOptions(options)),
			placeholderData: keepPreviousData
		};
	});

	const visible = $derived(sortLabels(labels.data ?? [], sort, getLocale()));
	const title = $derived(kind === 'scenes' ? m.explore_scenes() : m.explore_objects());
</script>

<svelte:head>
	<title>{title} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader {title}>
		{#snippet tools()}
			<label class="search">
				<Icon name="search" size={18} />
				<input
					type="search"
					bind:value={search}
					aria-label={m.explore_label_search({ kind: title })}
					placeholder={m.explore_label_search({ kind: title })}
				/>
			</label>
			<label class="sort">
				<span>{m.explore_sort()}</span>
				<select value={sort} onchange={(event) => setSort(event.currentTarget.value as LabelSort)}>
					<option value="count">{m.explore_sort_count()}</option>
					<option value="name">{m.explore_sort_name()}</option>
				</select>
			</label>
		{/snippet}
	</PageHeader>
	<PageCrumbs href={appHref('/explore')} label={m.nav_explore()} />

	{#if labels.isPending}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if labels.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if visible.length === 0}
		<p class="status">
			{query
				? m.explore_label_no_match()
				: kind === 'scenes'
					? m.explore_scenes_empty()
					: m.explore_objects_empty()}
		</p>
	{:else}
		<p class="visually-hidden" role="status">
			{m.explore_label_count({ count: visible.length })}
		</p>
		<ul class="grid" role="list" aria-label={title} aria-busy={labels.isFetching}>
			{#each visible as label (label.label)}
				<li>
					<LabelTile
						href={labelHref(kind, label.label)}
						label={label.label}
						count={label.assetCount}
						coverAssetId={label.coverAssetId}
					/>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: 0 var(--space-3);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		color: var(--color-text-muted);
	}

	.search:focus-within {
		outline: 2px solid var(--color-focus);
	}

	.search input {
		width: 14rem;
		padding: var(--space-2) 0;
		border: 0;
		background: transparent;
		color: var(--color-text);
		outline: none;
	}

	.sort {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.sort select {
		padding: var(--space-1) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: var(--space-3);
		margin: 0;
		padding: var(--space-4);
		list-style: none;
	}

	.status {
		padding: var(--space-6) var(--space-4);
		color: var(--color-text-muted);
	}
</style>
