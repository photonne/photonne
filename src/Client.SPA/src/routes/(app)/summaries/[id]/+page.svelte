<script lang="ts">
	import { page } from '$app/state';
	import { m } from '#lib/paraglide/messages.js';
	import SummaryReview from '#lib/summaries/SummaryReview.svelte';

	const id = $derived(page.params.id ?? '');
</script>

<svelte:head>
	<title>{m.summaries_review_title()} · {m.app_name()}</title>
</svelte:head>

{#key id}
	<SummaryReview {id} />
{/key}
