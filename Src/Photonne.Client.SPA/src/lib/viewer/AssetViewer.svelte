<script lang="ts" module>
	/**
	 * What changed on the open asset. `added`: a new asset was created next to
	 * it (a Live Photo frame saved as a still), so lists should reload.
	 */
	export type AssetChange = 'favorite' | 'description' | 'date' | 'tags' | 'added';

	// Material Icons (Apache 2.0) the shared set doesn't have.
	const slideshowPath =
		'M10 8v8l5-4-5-4zm9-5H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z';
	const framePath =
		'M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z';
	const playPath = 'M8 5v14l11-7z';
	const pausePath = 'M6 19h4V5H6v14zm8-14v14h4V5h-4z';
</script>

<script lang="ts">
	import { onMount, tick, type Snippet } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { toggleFavorite } from '#lib/api/index.js';
	import {
		getApiAssetsByIdFacesOptions,
		getAssetDetailOptions,
		getAssetDetailQueryKey,
		getTimelineBucketsQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from '#lib/people/icons.js';
	import { asUtc } from './capture-date.js';
	import FaceBoxes from './FaceBoxes.svelte';
	import { visibleFaces } from './faces.js';
	import FramePicker from './FramePicker.svelte';
	import {
		autoAdvanceDelay,
		parseInterval,
		SLIDESHOW_INTERVALS,
		slideshowCommand,
		type SlideshowInterval
	} from './slideshow.js';
	import ViewerPanel, { PANEL_TABS, type PanelTab } from './ViewerPanel.svelte';
	import PopupMenu, { type MenuEntry } from '#lib/timeline/PopupMenu.svelte';
	import ZoomableImage from './ZoomableImage.svelte';
	import { IDENTITY } from './zoom.js';

	interface Props {
		assetId: string;
		/** `thumbnailsGeneratedAt` of the asset, to hit the cached Large thumbnail. */
		thumbnailVersion?: string | null;
		previous: string | null;
		next: string | null;
		onnavigate: (assetId: string) => void;
		onclose: () => void;
		onchanged: (assetId: string, change: AssetChange) => void;
		/** Large-thumbnail versions of the neighbours, to preload them. */
		neighborVersion?: (assetId: string) => string | null | undefined;
		/** Offered only when the host passes them (not every surface can). */
		ontrash?: () => void;
		onarchive?: () => void;
		onaddtoalbum?: () => void;
		onshare?: () => void;
		/** Download through the host (format choice); a plain link to the original otherwise. */
		ondownload?: () => void;
		/** A page's own buttons for the open photo (restore in the trash…), before the standard ones. */
		actions?: Snippet;
	}

	let {
		assetId,
		thumbnailVersion = null,
		previous,
		next,
		onnavigate,
		onclose,
		onchanged,
		neighborVersion = () => null,
		ontrash,
		onarchive,
		onaddtoalbum,
		onshare,
		ondownload,
		actions
	}: Props = $props();

	const INFO_KEY = 'photonne.viewer.info';
	const TAB_KEY = 'photonne.viewer.tab';
	const INTERVAL_KEY = 'photonne.viewer.slideshow';

	const queryClient = useQueryClient();
	const detail = createQuery(() => getAssetDetailOptions({ path: { assetId } }));

	let dialog = $state<HTMLDivElement>();
	let closeButton = $state<HTMLButtonElement>();
	let image = $state<ZoomableImage>();
	let zoom = $state(IDENTITY);
	let showInfo = $state(readPreference(INFO_KEY) === '1');
	let tab = $state<PanelTab>(parseTab(readPreference(TAB_KEY)));
	let playingLive = $state(false);
	let showFaces = $state(false);
	let selectedFaceId = $state<string | null>(null);
	let pickingFrame = $state(false);

	// Slideshow: `paused` keeps the chrome hidden; Space resumes.
	let slideshow = $state(false);
	let paused = $state(false);
	let interval = $state<SlideshowInterval>(parseInterval(readPreference(INTERVAL_KEY)));
	let enteredFullscreen = false;
	let controlsIdle = $state(false);

	const asset = $derived(detail.data);
	const isVideo = $derived(asset?.type === 'Video');
	const isLive = $derived(asset?.tags.includes('LivePhoto') ?? false);
	const canPickFrame = $derived(isLive && !isVideo && (asset?.canSaveMotionFrame ?? false));
	const previewSrc = $derived(thumbnailUrl(assetId, 'Large', thumbnailVersion));
	const contentSrc = $derived(
		`/api/assets/${assetId}/content${asset ? `?v=${asset.checksum}` : ''}`
	);

	// Everything past favourite, share and info waits in the "⋮" menu; the
	// keys (S, Shift+F, Delete…) still work with it closed.
	const moreItems: MenuEntry[] = $derived.by(() => {
		const items: MenuEntry[] = [];
		if (onaddtoalbum)
			items.push({ label: m.action_add_to_album(), icon: 'albumAdd', run: onaddtoalbum });
		items.push(
			ondownload
				? { label: m.viewer_download(), icon: 'download', run: ondownload }
				: {
						kind: 'link',
						label: m.viewer_download(),
						icon: 'download',
						href: `/api/assets/${assetId}/content?download=true`,
						download: true
					}
		);
		items.push({
			label: m.viewer_slideshow_start(),
			icon: { path: slideshowPath },
			shortcut: 'S',
			run: startSlideshow
		});
		if (!isVideo)
			items.push({
				kind: 'check',
				label: m.viewer_faces_boxes(),
				icon: { path: icons.face },
				shortcut: m.viewer_key_faces(),
				keys: 'Shift+F',
				checked: showFaces,
				disabled: !asset,
				run: toggleFaces
			});
		if (canPickFrame)
			items.push({ label: m.viewer_frame_title(), icon: { path: framePath }, run: pickFrame });
		if (onarchive || ontrash) items.push({ kind: 'separator', id: 'remove' });
		if (onarchive) items.push({ label: m.action_archive(), icon: 'archive', run: onarchive });
		if (ontrash)
			items.push({
				label: m.action_trash(),
				icon: 'delete',
				shortcut: m.viewer_key_delete(),
				keys: 'Delete',
				danger: true,
				run: ontrash
			});
		return items;
	});

	function pickFrame() {
		pickingFrame = true;
	}

	// Boxes are drawn on demand; the list in the panel loads them on its own.
	const faces = createQuery(() => ({
		...getApiAssetsByIdFacesOptions({ path: { id: assetId } }),
		enabled: showFaces && !isVideo,
		retry: false
	}));
	const boxes = $derived(showFaces && !isVideo && !slideshow ? visibleFaces(faces.data ?? []) : []);

	// Each asset starts with its Live Photo still, not playing, and no face picked.
	$effect.pre(() => {
		void assetId;
		playingLive = false;
		selectedFaceId = null;
		pickingFrame = false;
	});

	// Warm the cache for the photos the arrows lead to.
	$effect(() => {
		for (const id of [previous, next]) {
			if (id) new Image().src = thumbnailUrl(id, 'Large', neighborVersion(id));
		}
	});

	// The slideshow moves on by itself after the interval; a video plays to
	// its end first (see onended), and at the last photo it waits.
	$effect(() => {
		void assetId;
		const delay = autoAdvanceDelay({
			running: slideshow && !paused,
			isVideo,
			hasNext: next !== null,
			interval
		});
		if (delay === null) return;
		const timer = setTimeout(advance, delay);
		return () => clearTimeout(timer);
	});

	onMount(() => {
		// The dialog itself takes focus, not its first button: no ring on open,
		// and Tab still reaches the controls from here.
		dialog?.focus();
		const previousOverflow = document.body.style.overflow;
		document.body.style.overflow = 'hidden';
		// Leaving fullscreen (the browser's own Esc) ends the slideshow too.
		const onFullscreen = () => {
			if (!document.fullscreenElement && enteredFullscreen) {
				enteredFullscreen = false;
				if (slideshow) stopSlideshow();
			}
		};
		document.addEventListener('fullscreenchange', onFullscreen);
		return () => {
			document.body.style.overflow = previousOverflow;
			document.removeEventListener('fullscreenchange', onFullscreen);
			if (enteredFullscreen && document.fullscreenElement) document.exitFullscreen?.();
		};
	});

	function readPreference(key: string) {
		try {
			return localStorage.getItem(key);
		} catch {
			return null;
		}
	}

	function writePreference(key: string, value: string) {
		try {
			localStorage.setItem(key, value);
		} catch {
			// Preference just isn't remembered.
		}
	}

	function parseTab(value: string | null): PanelTab {
		return PANEL_TABS.includes(value as PanelTab) ? (value as PanelTab) : 'info';
	}

	function toggleInfo() {
		showInfo = !showInfo;
		writePreference(INFO_KEY, showInfo ? '1' : '0');
	}

	function openTab(next: PanelTab) {
		tab = next;
		writePreference(TAB_KEY, next);
		if (!showInfo) toggleInfo();
	}

	function toggleFaces() {
		if (isVideo) return;
		showFaces = !showFaces;
		if (!showFaces) selectedFaceId = null;
	}

	/** A face picked on the photo: the panel shows it in the faces list. */
	function selectFace(faceId: string) {
		selectedFaceId = selectedFaceId === faceId ? null : faceId;
		if (selectedFaceId) openTab('faces');
	}

	async function favorite() {
		if (!asset) return;
		const { data } = await toggleFavorite({ path: { assetId: asset.id } });
		if (!data) return;
		queryClient.setQueryData(getAssetDetailQueryKey({ path: { assetId: asset.id } }), {
			...asset,
			isFavorite: data.isFavorite
		});
		onchanged(asset.id, 'favorite');
	}

	function changed(change: AssetChange) {
		queryClient.invalidateQueries({
			queryKey: getAssetDetailQueryKey({ path: { assetId } })
		});
		onchanged(assetId, change);
	}

	function frameSaved(newAssetId: string) {
		const original = assetId;
		pickingFrame = false;
		queryClient.invalidateQueries({ queryKey: getTimelineBucketsQueryKey() });
		onchanged(original, 'added');
		toasts.show(m.viewer_frame_saved(), {
			action: { label: m.viewer_frame_open(), run: () => onnavigate(newAssetId) }
		});
	}

	// --- Slideshow -------------------------------------------------------

	function advance() {
		if (next) onnavigate(next);
	}

	async function startSlideshow() {
		if (slideshow) return;
		image?.reset();
		playingLive = false;
		pickingFrame = false;
		slideshow = true;
		paused = false;
		controlsIdle = false;
		// Fullscreen when the browser allows it; the slideshow works without.
		if (!document.fullscreenElement && dialog?.requestFullscreen) {
			try {
				await dialog.requestFullscreen();
				enteredFullscreen = true;
			} catch {
				enteredFullscreen = false;
			}
		}
		dialog?.focus();
	}

	async function stopSlideshow() {
		slideshow = false;
		paused = false;
		if (enteredFullscreen && document.fullscreenElement) {
			enteredFullscreen = false;
			document.exitFullscreen?.().catch(() => {});
		}
		// The bar comes back: focus returns to it.
		await tick();
		closeButton?.focus();
	}

	function changeInterval(event: Event) {
		interval = parseInterval((event.currentTarget as HTMLSelectElement).value);
		writePreference(INTERVAL_KEY, String(interval));
		// Back to the slideshow's keys (Space pauses rather than reopening the list).
		dialog?.focus();
	}

	// The controls fade out while the photos play and come back on movement.
	$effect(() => {
		if (!slideshow || paused || controlsIdle) return;
		const timer = setTimeout(() => (controlsIdle = true), 2500);
		return () => clearTimeout(timer);
	});

	function wake() {
		controlsIdle = false;
	}

	function videoEnded() {
		if (slideshow && !paused) advance();
	}

	// --- Keyboard --------------------------------------------------------

	function onkeydown(event: KeyboardEvent) {
		const target = event.target as HTMLElement;
		// A dialog over the viewer (person picker…) owns its keys, Escape included.
		if (target.closest('dialog')) return;
		const typing = target.closest('input, textarea, select, [contenteditable="true"]');

		if (slideshow) return slideshowKey(event, !!typing);
		if (pickingFrame) {
			if (event.key === 'Escape') {
				event.preventDefault();
				pickingFrame = false;
			}
			if (event.key === 'Tab') trapFocus(event);
			return;
		}
		if (event.key === 'Escape') {
			event.preventDefault();
			if (zoom.scale > 1) image?.reset();
			else onclose();
			return;
		}
		if (event.key === 'Tab') return trapFocus(event);
		if (event.defaultPrevented) return;
		if (typing || event.ctrlKey || event.metaKey || event.altKey) return;

		const shortcuts: Record<string, () => void> = {
			ArrowLeft: () => previous && zoom.scale === 1 && onnavigate(previous),
			ArrowRight: () => next && zoom.scale === 1 && onnavigate(next),
			i: toggleInfo,
			f: favorite,
			F: toggleFaces,
			s: startSlideshow,
			'+': () => image?.zoomBy(1.5),
			'=': () => image?.zoomBy(1.5),
			'-': () => image?.zoomBy(1 / 1.5),
			'0': () => image?.reset(),
			Delete: () => ontrash?.()
		};
		const action = shortcuts[event.key];
		if (action) {
			event.preventDefault();
			action();
		}
	}

	function slideshowKey(event: KeyboardEvent, typing: boolean) {
		wake();
		const command = slideshowCommand(event.key);
		if (event.key === 'Tab') return trapFocus(event);
		// The interval picker keeps its own keys, except the way out.
		if (!command || (typing && command !== 'exit')) return;
		event.preventDefault();
		if (command === 'exit') stopSlideshow();
		else if (command === 'toggle') paused = !paused;
		else if (command === 'next') advance();
		else if (previous) onnavigate(previous);
	}

	function trapFocus(event: KeyboardEvent) {
		const focusable = [
			...dialog!.querySelectorAll<HTMLElement>(
				'a[href], button:not([disabled]), input, textarea, select, video, [tabindex]:not([tabindex="-1"])'
			)
		].filter((element) => element.offsetParent !== null || element === document.activeElement);
		if (focusable.length === 0) return;
		const first = focusable[0];
		const last = focusable.at(-1)!;
		if (document.activeElement === dialog) {
			event.preventDefault();
			(event.shiftKey ? last : first).focus();
		} else if (event.shiftKey && document.activeElement === first) {
			event.preventDefault();
			last.focus();
		} else if (!event.shiftKey && document.activeElement === last) {
			event.preventDefault();
			first.focus();
		}
	}
</script>

<!-- Shortcuts work wherever the focus is while the viewer is open. -->
<svelte:window {onkeydown} />

<div
	class="viewer"
	class:slideshow
	class:idle={slideshow && controlsIdle}
	role="dialog"
	aria-modal="true"
	aria-label={asset?.fileName ?? m.viewer_title()}
	tabindex="-1"
	bind:this={dialog}
	onpointermove={slideshow ? wake : undefined}
>
	<div class="main">
		{#if !slideshow}
			<header class="bar">
				<button
					type="button"
					class="icon-btn"
					bind:this={closeButton}
					title="{m.viewer_close()} · Esc"
					aria-label={m.viewer_close()}
					aria-keyshortcuts="Escape"
					onclick={onclose}
				>
					<Icon name="close" />
				</button>
				<div class="title">
					{#if asset}
						<span>{dateTime(asUtc(asset.capturedAt))}</span>
						<span class="muted">{asset.fileName}</span>
					{/if}
				</div>
				<div class="actions">
					{@render actions?.()}
					{#if isLive && !isVideo}
						<button
							type="button"
							class="live"
							aria-pressed={playingLive}
							onclick={() => (playingLive = !playingLive)}
						>
							<Icon name="livePhoto" size={18} />
							LIVE
						</button>
					{/if}
					{#if onshare}
						<button
							type="button"
							class="icon-btn"
							title={m.viewer_share()}
							aria-label={m.viewer_share()}
							onclick={onshare}
						>
							<Icon name="share" />
						</button>
					{/if}
					<button
						type="button"
						class="icon-btn"
						aria-pressed={asset?.isFavorite ?? false}
						aria-keyshortcuts="F"
						title="{asset?.isFavorite ? m.viewer_favorite_remove() : m.viewer_favorite_add()} · F"
						aria-label={asset?.isFavorite ? m.viewer_favorite_remove() : m.viewer_favorite_add()}
						disabled={!asset}
						onclick={favorite}
					>
						<Icon name={asset?.isFavorite ? 'favorite' : 'favoriteOutline'} />
					</button>
					<button
						type="button"
						class="icon-btn"
						aria-pressed={showInfo}
						aria-keyshortcuts="I"
						title="{m.viewer_info()} · I"
						aria-label={m.viewer_info()}
						onclick={toggleInfo}
					>
						<Icon name="info" />
					</button>
					<PopupMenu label={m.viewer_more()} items={moreItems} />
				</div>
			</header>
		{/if}

		<div class="stage">
			{#key slideshow ? assetId : null}
				<div class="slide">
					{#if isVideo}
						<!-- svelte-ignore a11y_media_has_caption -->
						<video
							src={contentSrc}
							poster={previewSrc}
							controls={!slideshow}
							autoplay
							playsinline
							aria-label={asset?.fileName}
							onended={videoEnded}
							onerror={videoEnded}
						></video>
					{:else}
						<ZoomableImage
							bind:this={image}
							bind:zoom
							{previewSrc}
							fullSrc={contentSrc}
							alt={asset?.caption || asset?.fileName || ''}
							overlay={boxes.length ? faceOverlay : undefined}
						/>
						{#if playingLive}
							<video
								class="live-clip"
								src="/api/assets/{assetId}/motion"
								autoplay
								muted
								playsinline
								onended={() => (playingLive = false)}
							></video>
						{/if}
					{/if}
				</div>
			{/key}

			{#if !slideshow}
				{#if previous}
					<button
						type="button"
						class="nav previous"
						aria-label={m.viewer_previous()}
						onclick={() => onnavigate(previous)}
					>
						<Icon name="chevronLeft" size={32} />
					</button>
				{/if}
				{#if next}
					<button
						type="button"
						class="nav next"
						aria-label={m.viewer_next()}
						onclick={() => onnavigate(next)}
					>
						<Icon name="chevronRight" size={32} />
					</button>
				{/if}
			{/if}
		</div>

		{#if slideshow}
			<div class="slideshow-bar" role="toolbar" aria-label={m.viewer_slideshow_label()}>
				<button
					type="button"
					class="icon-btn"
					aria-label={m.viewer_previous()}
					disabled={!previous}
					onclick={() => previous && onnavigate(previous)}
				>
					<Icon name="chevronLeft" />
				</button>
				<button
					type="button"
					class="icon-btn"
					aria-keyshortcuts="Space"
					aria-label={paused ? m.viewer_slideshow_play() : m.viewer_slideshow_pause()}
					onclick={() => (paused = !paused)}
				>
					<Icon path={paused ? playPath : pausePath} />
				</button>
				<button
					type="button"
					class="icon-btn"
					aria-label={m.viewer_next()}
					disabled={!next}
					onclick={advance}
				>
					<Icon name="chevronRight" />
				</button>
				<select
					aria-label={m.viewer_slideshow_interval()}
					value={interval}
					onchange={changeInterval}
				>
					{#each SLIDESHOW_INTERVALS as seconds (seconds)}
						<option value={seconds}>{m.viewer_slideshow_seconds({ seconds })}</option>
					{/each}
				</select>
				<span class="state" role="status">
					{#if paused}{m.viewer_slideshow_paused()}{:else if !next}{m.viewer_slideshow_end()}{/if}
				</span>
				<button
					type="button"
					class="icon-btn"
					aria-keyshortcuts="Escape"
					aria-label={m.viewer_slideshow_exit()}
					onclick={stopSlideshow}
				>
					<Icon name="close" />
				</button>
			</div>
		{/if}

		{#if pickingFrame && !slideshow}
			<FramePicker {assetId} onclose={() => (pickingFrame = false)} onsaved={frameSaved} />
		{/if}
	</div>

	{#if showInfo && asset && !slideshow}
		<ViewerPanel
			{asset}
			{tab}
			ontab={openTab}
			onchanged={changed}
			onopen={onnavigate}
			{selectedFaceId}
			onselectface={(id) => (selectedFaceId = id)}
			showFaceBoxes={showFaces}
			ontogglefaceboxes={toggleFaces}
		/>
	{/if}
</div>

{#snippet faceOverlay(scale: number)}
	<FaceBoxes faces={boxes} selectedId={selectedFaceId} {scale} onselect={selectFace} />
{/snippet}

<style>
	/* The viewer is always dark, whatever the app theme: the photo is the light. */
	.viewer {
		--color-bg: #111214;
		--color-surface: #26282c;
		--color-surface-raised: #1b1c1f;
		--color-border: #34363b;
		--color-border-strong: #70747c;
		--color-hover: rgb(255 255 255 / 0.12);
		--color-text: #e8e8ea;
		--color-text-muted: #a0a3a8;
		--color-brand: #ffd166;
		--color-accent: #ffd166;
		--color-accent-text: #1a1a2e;
		--color-accent-soft: rgb(255 209 102 / 0.14);
		--color-focus: #ffd166;
		--color-danger: #ef9a9a;
		--color-success: #7bd88f;
		--color-warning: #f6b35a;
		--color-placeholder: #26282c;
		--shadow-raised: 0 1px 2px rgb(0 0 0 / 0.4), 0 4px 16px rgb(0 0 0 / 0.5);
		color-scheme: dark;
		position: fixed;
		inset: 0;
		z-index: 50;
		display: flex;
		background: #000;
		color: #fff;
		outline: none;
	}

	.viewer.idle {
		cursor: none;
	}

	.main {
		position: relative;
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}

	.bar {
		position: absolute;
		inset: 0 0 auto 0;
		z-index: 2;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3);
		background: linear-gradient(rgb(0 0 0 / 0.6), transparent);
	}

	.title {
		display: grid;
		line-height: 1.3;
		min-width: 0;
	}

	.title span {
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.muted {
		font-size: var(--font-size-sm);
		color: rgb(255 255 255 / 0.7);
	}

	.actions {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: var(--space-1);
	}

	/* On the photo, the bar's buttons are white whatever is under them. */
	.bar :global(.icon-btn) {
		color: #fff;
	}

	.bar :global(.icon-btn[aria-pressed='true']) {
		color: var(--color-accent);
	}

	.live {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		height: var(--control-h-sm);
		margin-right: var(--space-1);
		padding: 0 var(--space-3);
		border: 1px solid rgb(255 255 255 / 0.5);
		border-radius: 999px;
		background: transparent;
		color: inherit;
		font-size: var(--font-size-xs);
		font-weight: 700;
		letter-spacing: 0.05em;
		cursor: pointer;
	}

	.live[aria-pressed='true'] {
		background: rgb(255 255 255 / 0.2);
	}

	.stage {
		position: relative;
		flex: 1;
		min-height: 0;
		display: grid;
		place-items: center;
	}

	.slide {
		position: relative;
		width: 100%;
		height: 100%;
		display: grid;
		place-items: center;
	}

	/* Each slide fades in; with reduced motion it just appears. */
	.slideshow .slide {
		animation: slide-in 600ms ease-out;
	}

	@media (prefers-reduced-motion: reduce) {
		.slideshow .slide {
			animation: none;
		}
	}

	@keyframes slide-in {
		from {
			opacity: 0;
		}
	}

	video {
		max-width: 100%;
		max-height: 100%;
	}

	.live-clip {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: contain;
		background: #000;
	}

	.nav {
		position: absolute;
		top: 50%;
		transform: translateY(-50%);
		display: grid;
		place-items: center;
		width: 56px;
		height: 56px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.35);
		color: #fff;
		cursor: pointer;
		opacity: 0.7;
	}

	.nav:hover,
	.nav:focus-visible {
		opacity: 1;
	}

	.previous {
		left: var(--space-3);
	}

	.next {
		right: var(--space-3);
	}

	.slideshow-bar {
		position: absolute;
		left: 50%;
		bottom: var(--space-4);
		z-index: 2;
		display: flex;
		align-items: center;
		gap: var(--space-1);
		padding: var(--space-1) var(--space-2);
		border-radius: 999px;
		background: rgb(0 0 0 / 0.65);
		transform: translateX(-50%);
		transition: opacity var(--duration-normal);
	}

	.idle .slideshow-bar:not(:focus-within) {
		opacity: 0;
	}

	.slideshow-bar select {
		min-height: var(--control-h-sm);
		border-color: rgb(255 255 255 / 0.3);
		background-color: transparent;
	}

	.state {
		min-width: 0;
		padding: 0 var(--space-2);
		font-size: var(--font-size-sm);
		color: rgb(255 255 255 / 0.75);
		white-space: nowrap;
	}

	.state:empty {
		padding: 0;
	}
</style>
