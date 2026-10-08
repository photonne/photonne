<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import FolderCards from '#lib/albums/FolderCards.svelte';
	import { sortTree } from '#lib/albums/folder-tree.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const treeQuery = createQuery(() => getFolderTreeOptions());
	const roots = $derived(sortTree(treeQuery.data ?? [], getLocale()));
</script>

<svelte:head>
	<title>{m.nav_folders()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<h1>{m.nav_folders()}</h1>
	{#if treeQuery.isPending}
		<p class="note" role="status">{m.session_restoring()}</p>
	{:else if treeQuery.isError}
		<p class="note" role="alert">{m.error_loading()}</p>
	{:else if roots.length === 0}
		<div class="empty">
			<Icon name="folder" size={48} />
			<p class="title">{m.folders_none()}</p>
			<p>{m.folders_none_body()}</p>
		</div>
	{:else}
		<p class="note">{m.folders_intro()}</p>
		<FolderCards folders={roots} label={m.folders_roots()} />
	{/if}
</div>

<style>
	.page {
		display: grid;
		gap: var(--space-4);
		align-content: start;
		padding: var(--space-4);
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	.note {
		margin: 0;
		color: var(--color-text-muted);
	}

	.empty {
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		padding: var(--space-8) var(--space-4);
		color: var(--color-text-muted);
		text-align: center;
	}

	.empty p {
		margin: 0;
	}

	.empty .title {
		font-size: var(--font-size-lg);
		font-weight: 600;
		color: var(--color-text);
	}
</style>
