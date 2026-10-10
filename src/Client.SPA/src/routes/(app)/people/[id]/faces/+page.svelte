<script lang="ts">
	import PersonFaces from '#lib/people/PersonFaces.svelte';
	import PersonFrame from '#lib/people/PersonFrame.svelte';
	import type { PageProps } from './$types';

	let { params }: PageProps = $props();
</script>

<PersonFrame id={params.id} tab="faces">
	{#snippet children(person)}
		<PersonFaces {person} />
	{/snippet}
</PersonFrame>
