<script lang="ts">
	import FolderDetail from '#lib/albums/FolderDetail.svelte';
	import type { PageProps } from './$types';

	let { params }: PageProps = $props();
</script>

<!-- Each folder gets its own view: the grid, selection and batch actions are
     built around one folder's list. The tree (layout) stays mounted. -->
{#key params.folderId}
	<FolderDetail folderId={params.folderId} />
{/key}
