<script lang="ts">
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import LinksPanel from './LinksPanel.svelte';
	import PermissionsPanel from './PermissionsPanel.svelte';

	interface Props {
		kind: 'album' | 'folder';
		targetId: string;
		name: string;
		/** Albums: the viewer may grant access to people. */
		canManagePeople: boolean;
		/** Albums: the viewer may create public links (owner or write access). */
		canLink: boolean;
		/** Open on the links tab. */
		initialTab?: 'people' | 'links';
		onclose: () => void;
		onchanged: () => void;
	}

	let {
		kind,
		targetId,
		name,
		canManagePeople,
		canLink,
		initialTab = 'people',
		onclose,
		onchanged
	}: Props = $props();

	const id = $props.id();
	const tabs = $derived(
		[
			canManagePeople ? ({ key: 'people', label: m.albums_share_people() } as const) : null,
			kind === 'album' && canLink
				? ({ key: 'links', label: m.albums_share_links() } as const)
				: null
		].filter((tab) => tab !== null)
	);

	let chosen = $state<'people' | 'links' | null>(null);
	const active = $derived(
		tabs.find((tab) => tab.key === (chosen ?? initialTab))?.key ?? tabs[0]?.key ?? 'people'
	);

	function onTabKey(event: KeyboardEvent) {
		if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
		event.preventDefault();
		const index = tabs.findIndex((tab) => tab.key === active);
		const next = tabs[(index + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
		chosen = next.key;
		document.getElementById(`${id}-tab-${next.key}`)?.focus();
	}
</script>

<Dialog open title={m.albums_share_title({ name })} {onclose} width="600px">
	{#if tabs.length > 1}
		<div class="tabs" role="tablist" aria-label={m.albums_share_title({ name })}>
			{#each tabs as tab (tab.key)}
				<button
					type="button"
					role="tab"
					id="{id}-tab-{tab.key}"
					aria-selected={active === tab.key}
					aria-controls="{id}-panel"
					tabindex={active === tab.key ? 0 : -1}
					onclick={() => (chosen = tab.key)}
					onkeydown={onTabKey}
				>
					{tab.label}
				</button>
			{/each}
		</div>
	{/if}

	<div
		id="{id}-panel"
		role={tabs.length > 1 ? 'tabpanel' : undefined}
		aria-labelledby={tabs.length > 1 ? `${id}-tab-${active}` : undefined}
	>
		{#if active === 'links'}
			<LinksPanel albumId={targetId} {onchanged} />
		{:else}
			<PermissionsPanel {kind} {targetId} {onchanged} />
		{/if}
	</div>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.albums_close()}</button>
	{/snippet}
</Dialog>

<style>
	.tabs {
		display: flex;
		gap: var(--space-1);
		margin-bottom: var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}

	[role='tab'] {
		padding: var(--space-2) var(--space-3);
		border: 0;
		border-bottom: 2px solid transparent;
		background: transparent;
		color: var(--color-text-muted);
		font-weight: 600;
		cursor: pointer;
	}

	[role='tab'][aria-selected='true'] {
		border-bottom-color: var(--color-accent);
		color: var(--color-text);
	}
</style>
