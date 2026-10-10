<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getApiPeopleByIdOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { m } from '#lib/paraglide/messages.js';
	import { displayName } from '#lib/people/people.js';

	// A photo shows a handful of faces: one cached query per person beats
	// loading the whole people list to name them.
	let { id }: { id: string } = $props();

	const person = createQuery(() => getApiPeopleByIdOptions({ path: { id } }));
</script>

{#if person.data}{displayName(person.data, m.people_unnamed())}{:else}…{/if}
