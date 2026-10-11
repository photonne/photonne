<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import { whenVisible } from './visible.js';

	interface Props {
		/** The infinite query whose next page loads at the end of the list. */
		query: {
			hasNextPage: boolean;
			isFetchingNextPage: boolean;
			isFetchNextPageError: boolean;
			fetchNextPage: () => unknown;
			data?: { pages: readonly unknown[] };
		};
	}

	let { query }: Props = $props();
</script>

<!-- The end of a paged list: a sentinel that loads the next page when it scrolls
     near, or, when that page failed, a way to ask again (the sentinel alone would
     just sit there). -->
{#if query.isFetchNextPageError}
	<div class="failed" role="alert">
		<span>{m.people_more_failed()}</span>
		<button type="button" class="btn sm" onclick={() => query.fetchNextPage()}>
			{m.people_retry()}
		</button>
	</div>
{:else if query.hasNextPage}
	{#key query.data?.pages.length}
		<div
			class="sentinel"
			{@attach whenVisible(() => {
				if (!query.isFetchingNextPage) query.fetchNextPage();
			})}
		></div>
	{/key}
{/if}

<style>
	.sentinel {
		height: 1px;
	}

	.failed {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-3);
		padding: var(--space-4);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}
</style>
