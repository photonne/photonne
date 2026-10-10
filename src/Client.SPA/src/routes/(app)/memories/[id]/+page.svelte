<script lang="ts">
	import { page } from '$app/state';
	import MemoryDetail from '#lib/library/MemoryDetail.svelte';

	const id = $derived(page.params.id ?? '');
</script>

{#key id}
	<MemoryDetail {id} />
{/key}
