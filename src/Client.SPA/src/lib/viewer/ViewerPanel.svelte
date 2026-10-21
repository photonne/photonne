<script lang="ts" module>
	export type PanelTab = 'info' | 'faces' | 'ai';

	export const PANEL_TABS: readonly PanelTab[] = ['info', 'faces', 'ai'];
</script>

<script lang="ts">
	import { MediaQuery } from 'svelte/reactivity';
	import type { AssetDetailResponse } from '#lib/api/index.js';
	import PaneResizer from '#lib/components/PaneResizer.svelte';
	import { clampWidth, savedWidth } from '#lib/components/pane-width.js';
	import Icon from '#lib/components/Icon.svelte';
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
		onclose: () => void;
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
		ontogglefaceboxes,
		onclose
	}: Props = $props();

	const id = $props.id();
	let tablist = $state<HTMLElement>();

	const PANEL = { initial: 340, min: 280, max: 720, key: 'photonne.viewer.panelWidth' };
	let width = $state(savedWidth(PANEL.key, PANEL.initial, PANEL.min, PANEL.max));
	// Never so wide that the photo is left without room.
	let innerWidth = $state(1280);
	const max = $derived(Math.max(PANEL.min, Math.min(PANEL.max, innerWidth * 0.6)));
	const shown = $derived(clampWidth(width, PANEL.min, max));
	// On a phone the panel is a bottom sheet: nothing to resize.
	const narrow = new MediaQuery('max-width: 700px');

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

<svelte:window bind:innerWidth />

{#if !narrow.current}
	<PaneResizer
		bind:width={() => shown, (value) => (width = value)}
		min={PANEL.min}
		{max}
		initial={PANEL.initial}
		storageKey={PANEL.key}
		controls="viewer-panel"
		side="right"
		quiet
	/>
{/if}

<aside id="viewer-panel" class="panel" style:--panel-width="{shown}px" aria-labelledby="info-title">
	<header class="head">
		<h2 id="info-title">{m.info_title()}</h2>
		<button
			type="button"
			class="icon-btn"
			title="{m.viewer_info_close()} · I"
			aria-label={m.viewer_info_close()}
			onclick={onclose}
		>
			<Icon name="close" />
		</button>
	</header>

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
	/* An island beside the photo, like the app's navigation. */
	/* Positioned, after the photo: over its side (the resize handle stays
	   on top, by its own z-index). */
	.panel {
		position: relative;
		flex: none;
		width: var(--panel-width);
		height: calc(100% - 2 * var(--float-margin));
		margin: var(--float-margin) var(--float-margin) var(--float-margin) 0;
		display: flex;
		flex-direction: column;
		overflow: hidden;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-xl);
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-nav);
		color: var(--color-text);
	}

	/* On a narrow screen a side column would squeeze the photo to nothing:
	   the panel becomes a sheet over the lower part of it instead. */
	@media (max-width: 700px) {
		.panel {
			position: absolute;
			inset: auto 0 0;
			z-index: 3;
			width: auto;
			height: min(60vh, 560px);
			margin: 0;
			border-width: 1px 0 0;
			border-radius: var(--radius-xl) var(--radius-xl) 0 0;
		}

		/* The sheet's grip. */
		.panel::before {
			content: '';
			align-self: center;
			flex: none;
			width: 36px;
			height: 4px;
			margin-top: var(--space-2);
			border-radius: var(--radius-pill);
			background: var(--color-border-strong);
		}
	}

	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-2) var(--space-1) var(--space-4);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-lg);
	}

	/* The tabs as a segmented pill. */
	.tabs {
		display: flex;
		gap: 2px;
		margin: var(--space-1) var(--space-4) var(--space-2);
		padding: 3px;
		border-radius: var(--radius-pill);
		background: var(--color-surface);
	}

	[role='tab'] {
		flex: 1;
		min-height: var(--control-h-sm);
		padding: 0 var(--space-2);
		border: 0;
		border-radius: var(--radius-pill);
		background: transparent;
		color: var(--color-text-muted);
		font-weight: 600;
		cursor: pointer;
		transition:
			background var(--duration-normal) var(--ease-standard),
			color var(--duration-fast);
	}

	[role='tab']:hover {
		color: var(--color-text);
	}

	[role='tab'][aria-selected='true'] {
		background: var(--color-active-pill);
		color: var(--color-text);
	}

	.body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: var(--space-4);
	}
</style>
