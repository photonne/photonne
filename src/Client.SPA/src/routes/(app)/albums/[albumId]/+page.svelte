<script lang="ts">
	import AlbumDetail from '#lib/albums/AlbumDetail.svelte';
	import type { PageProps } from './$types';

	let { params }: PageProps = $props();
</script>

<!-- A new album (a pinned link in the sidebar) is a new view: its grid, selection
     and batch actions are built around one album's list. -->
{#key params.albumId}
	<AlbumDetail albumId={params.albumId} />
{/key}
