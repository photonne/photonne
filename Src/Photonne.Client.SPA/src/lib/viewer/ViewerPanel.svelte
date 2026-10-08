<script lang="ts" module>
	export type PanelTab = 'info' | 'faces' | 'ai';

	export const PANEL_TABS: readonly PanelTab[] = ['info', 'faces', 'ai'];
</script>

<script lang="ts">
	import type { AssetDetailResponse } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import AiPanel from './AiPanel.svelte';
	import FacesPanel from './FacesPanel.svelte';
	import InfoPanel from './InfoPanel.svelte';

	interface Props {
		asset: AssetDetailResponse;
		tab: PanelTab;
		ontab: (tab: PanelTab) => void;
		onchanged: (change: 'description' | 'date' | 'tags') => void;
		onopen: (assetId: string) => void;
		selectedFaceId: string | null;
		onselectface: (faceId: string | null) => void;
		showFaceBoxes: boolean;
		ontogglefaceboxes: () => void;
	}

	let {
		asset,
		tab,
		ontab,
		onchanged,
		onopen,
		selectedFaceId,
		onselectface,
		showFaceBoxes,
		ontogglefaceboxes
	}: Props = $props();

	const id = $props.id();
	let tablist = $state<HTMLElement>();

	const labels: Record<PanelTab, () => string> = {
		info: m.viewer_tab_info,
		faces: m.viewer_tab_faces,
		ai: m.viewer_tab_ai
	};

	// Arrow keys move between tabs (WAI-ARIA tabs); handled here so the
	// viewer doesn't also take them as "next photo".
	function onkeydown(event: KeyboardEvent) {
		const index = PANEL_TABS.indexOf(tab);
		const targets: Record<string, number> = {
			ArrowLeft: (index + PANEL_TABS.length - 1) % PANEL_TABS.length,
			ArrowRight: (index + 1) % PANEL_TABS.length,
			Home: 0,
			End: PANEL_TABS.length - 1
		};
		const target = targets[event.key];
		if (target === undefined) return;
		event.preventDefault();
		ontab(PANEL_TABS[target]);
		tablist?.querySelectorAll<HTMLElement>('[role="tab"]')[target]?.focus();
	}
</script>

<aside class="panel" aria-labelledby="info-title">
	<h2 id="info-title">{m.info_title()}</h2>

	<!-- svelte-ignore a11y_interactive_supports_focus -->
	<div class="tabs" role="tablist" aria-label={m.viewer_tabs()} bind:this={tablist} {onkeydown}>
		{#each PANEL_TABS as name (name)}
			<button
				type="button"
				role="tab"
				id="{id}-{name}"
				aria-selected={tab === name}
				aria-controls="{id}-panel"
				tabindex={tab === name ? 0 : -1}
				onclick={() => ontab(name)}
			>
				{labels[name]()}
			</button>
		{/each}
	</div>

	<div class="body" role="tabpanel" id="{id}-panel" aria-labelledby="{id}-{tab}">
		{#if tab === 'faces'}
			<FacesPanel
				assetId={asset.id}
				selectedId={selectedFaceId}
				onselect={onselectface}
				showBoxes={showFaceBoxes}
				ontoggleboxes={ontogglefaceboxes}
			/>
		{:else if tab === 'ai'}
			<AiPanel assetId={asset.id} />
		{:else}
			<InfoPanel {asset} {onchanged} {onopen} />
		{/if}
	</div>
</aside>

<style>
	.panel {
		flex: none;
		width: 340px;
		height: 100%;
		display: flex;
		flex-direction: column;
		background: var(--color-surface-raised);
		color: var(--color-text);
	}

	h2 {
		margin: 0;
		padding: var(--space-4) var(--space-4) var(--space-2);
		font-size: var(--font-size-lg);
	}

	.tabs {
		display: flex;
		gap: var(--space-1);
		padding: 0 var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}

	[role='tab'] {
		flex: 1;
		padding: var(--space-2) var(--space-1);
		border: 0;
		border-bottom: 2px solid transparent;
		background: transparent;
		color: var(--color-text-muted);
		font-weight: 600;
		cursor: pointer;
	}

	[role='tab']:hover {
		color: var(--color-text);
	}

	[role='tab'][aria-selected='true'] {
		border-bottom-color: var(--color-accent);
		color: var(--color-text);
	}

	.body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: var(--space-4);
	}
</style>
