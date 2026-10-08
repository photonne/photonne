<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getTimelineBucketsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	// Provisional: the month skeleton only. Phase 2 replaces it with the
	// justified, virtualized timeline.
	const buckets = createQuery(() => getTimelineBucketsOptions());

	const monthFormat = $derived(
		new Intl.DateTimeFormat(getLocale(), { month: 'long', year: 'numeric', timeZone: 'UTC' })
	);

	function monthLabel(key: string) {
		const [year, month] = key.split('-').map(Number);
		return monthFormat.format(new Date(Date.UTC(year, month - 1, 1)));
	}
</script>

<svelte:head>
	<title>{m.photos_title()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<h1>{m.photos_title()}</h1>

	{#if buckets.isPending}
		<p class="muted" role="status">{m.session_restoring()}</p>
	{:else if buckets.isError}
		<p class="muted" role="alert">{m.error_loading()}</p>
	{:else if buckets.data.length === 0}
		<p class="muted">{m.photos_empty()}</p>
	{:else}
		<ul class="months">
			{#each buckets.data as bucket (bucket.key)}
				<li>
					<span class="month">{monthLabel(bucket.key)}</span>
					<span class="muted">{m.photos_month_items({ count: bucket.count })}</span>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.page {
		padding: var(--space-6);
	}

	h1 {
		margin: 0 0 var(--space-4);
		font-size: var(--font-size-xl);
	}

	.muted {
		color: var(--color-text-muted);
	}

	.months {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-2);
		max-width: 480px;
	}

	.months li {
		display: flex;
		justify-content: space-between;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
	}

	.month::first-letter {
		text-transform: uppercase;
	}
</style>
