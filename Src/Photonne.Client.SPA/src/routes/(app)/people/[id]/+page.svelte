<script lang="ts">
	import PersonFrame from '#lib/people/PersonFrame.svelte';
	import PersonPhotos from '#lib/people/PersonPhotos.svelte';
	import type { PageProps } from './$types';

	let { params }: PageProps = $props();
</script>

<PersonFrame id={params.id} tab="photos" header={false}>
	{#snippet children(person)}
		<PersonPhotos {person} />
	{/snippet}
</PersonFrame>
