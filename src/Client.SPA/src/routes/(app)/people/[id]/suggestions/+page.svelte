<script lang="ts">
	import PersonFrame from '#lib/people/PersonFrame.svelte';
	import PersonSuggestions from '#lib/people/PersonSuggestions.svelte';
	import type { PageProps } from './$types';

	let { params }: PageProps = $props();
</script>

<PersonFrame id={params.id} tab="suggestions">
	{#snippet children(person)}
		<PersonSuggestions {person} />
	{/snippet}
</PersonFrame>
