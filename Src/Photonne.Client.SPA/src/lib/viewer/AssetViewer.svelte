<script lang="ts">
	import { onMount } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { toggleFavorite } from '#lib/api/index.js';
	import {
		getAssetDetailOptions,
		getAssetDetailQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { dateTime } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import InfoPanel from './InfoPanel.svelte';
	import ZoomableImage from './ZoomableImage.svelte';
	import { IDENTITY } from './zoom.js';

	export type AssetChange = 'favorite' | 'description' | 'date' | 'tags';

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
		onaddtoalbum
	}: Props = $props();

	const INFO_KEY = 'photonne.viewer.info';

	const queryClient = useQueryClient();
	const detail = createQuery(() => getAssetDetailOptions({ path: { assetId } }));

	let dialog = $state<HTMLDivElement>();
	let closeButton = $state<HTMLButtonElement>();
	let image = $state<ZoomableImage>();
	let zoom = $state(IDENTITY);
	let showInfo = $state(readInfoPreference());
	let playingLive = $state(false);

	const asset = $derived(detail.data);
	const isVideo = $derived(asset?.type === 'Video');
	const isLive = $derived(asset?.tags.includes('LivePhoto') ?? false);
	const previewSrc = $derived(thumbnailUrl(assetId, 'Large', thumbnailVersion));
	const contentSrc = $derived(
		`/api/assets/${assetId}/content${asset ? `?v=${asset.checksum}` : ''}`
	);

	// Each asset starts with its Live Photo still, not playing.
	$effect.pre(() => {
		void assetId;
		playingLive = false;
	});

	// Warm the cache for the photos the arrows lead to.
	$effect(() => {
		for (const id of [previous, next]) {
			if (id) new Image().src = thumbnailUrl(id, 'Large', neighborVersion(id));
		}
	});

	onMount(() => {
		closeButton?.focus();
		const previousOverflow = document.body.style.overflow;
		document.body.style.overflow = 'hidden';
		return () => (document.body.style.overflow = previousOverflow);
	});

	function readInfoPreference() {
		try {
			return localStorage.getItem(INFO_KEY) === '1';
		} catch {
			return false;
		}
	}

	function toggleInfo() {
		showInfo = !showInfo;
		try {
			localStorage.setItem(INFO_KEY, showInfo ? '1' : '0');
		} catch {
			// Preference just isn't remembered.
		}
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

	function onkeydown(event: KeyboardEvent) {
		const target = event.target as HTMLElement;
		const typing = target.closest('input, textarea, select, [contenteditable="true"]');
		if (event.key === 'Escape') {
			event.preventDefault();
			if (zoom.scale > 1) image?.reset();
			else onclose();
			return;
		}
		if (event.key === 'Tab') return trapFocus(event);
		if (event.defaultPrevented) return;
		if (typing || event.ctrlKey || event.metaKey || event.altKey) return;

		const actions: Record<string, () => void> = {
			ArrowLeft: () => previous && zoom.scale === 1 && onnavigate(previous),
			ArrowRight: () => next && zoom.scale === 1 && onnavigate(next),
			i: toggleInfo,
			f: favorite,
			'+': () => image?.zoomBy(1.5),
			'=': () => image?.zoomBy(1.5),
			'-': () => image?.zoomBy(1 / 1.5),
			'0': () => image?.reset(),
			Delete: () => ontrash?.()
		};
		const action = actions[event.key];
		if (action) {
			event.preventDefault();
			action();
		}
	}

	function trapFocus(event: KeyboardEvent) {
		const focusable = [
			...dialog!.querySelectorAll<HTMLElement>(
				'a[href], button:not([disabled]), input, textarea, select, video, [tabindex]:not([tabindex="-1"])'
			)
		];
		if (focusable.length === 0) return;
		const first = focusable[0];
		const last = focusable.at(-1)!;
		if (event.shiftKey && document.activeElement === first) {
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
	role="dialog"
	aria-modal="true"
	aria-label={asset?.fileName ?? m.viewer_title()}
	tabindex="-1"
	bind:this={dialog}
>
	<div class="main">
		<header class="bar">
			<button
				type="button"
				class="icon"
				bind:this={closeButton}
				aria-label={m.viewer_close()}
				onclick={onclose}
			>
				<Icon name="close" />
			</button>
			<div class="title">
				{#if asset}
					<span>{dateTime(asset.capturedAt)}</span>
					<span class="muted">{asset.fileName}</span>
				{/if}
			</div>
			<div class="actions">
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
				<button
					type="button"
					class="icon"
					aria-pressed={asset?.isFavorite ?? false}
					aria-label={asset?.isFavorite ? m.viewer_favorite_remove() : m.viewer_favorite_add()}
					disabled={!asset}
					onclick={favorite}
				>
					<Icon name={asset?.isFavorite ? 'favorite' : 'favoriteOutline'} />
				</button>
				{#if onaddtoalbum}
					<button
						type="button"
						class="icon"
						aria-label={m.action_add_to_album()}
						onclick={onaddtoalbum}
					>
						<Icon name="albumAdd" />
					</button>
				{/if}
				{#if onarchive}
					<button type="button" class="icon" aria-label={m.action_archive()} onclick={onarchive}>
						<Icon name="archive" />
					</button>
				{/if}
				{#if ontrash}
					<button type="button" class="icon" aria-label={m.action_trash()} onclick={ontrash}>
						<Icon name="delete" />
					</button>
				{/if}
				<a
					class="icon"
					href="/api/assets/{assetId}/content?download=true"
					download
					aria-label={m.viewer_download()}
				>
					<Icon name="download" />
				</a>
				<button
					type="button"
					class="icon"
					aria-pressed={showInfo}
					aria-label={m.viewer_info()}
					onclick={toggleInfo}
				>
					<Icon name="info" />
				</button>
			</div>
		</header>

		<div class="stage">
			{#if isVideo}
				<!-- svelte-ignore a11y_media_has_caption -->
				<video
					src={contentSrc}
					poster={previewSrc}
					controls
					autoplay
					playsinline
					aria-label={asset?.fileName}
				></video>
			{:else}
				<ZoomableImage
					bind:this={image}
					bind:zoom
					{previewSrc}
					fullSrc={contentSrc}
					alt={asset?.caption || asset?.fileName || ''}
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
		</div>
	</div>

	{#if showInfo && asset}
		<InfoPanel {asset} onchanged={changed} />
	{/if}
</div>

<style>
	/* The viewer is always dark, whatever the app theme: the photo is the light. */
	.viewer {
		--color-bg: #111214;
		--color-surface: #26282c;
		--color-surface-raised: #1b1c1f;
		--color-border: #34363b;
		--color-text: #e8e8ea;
		--color-text-muted: #a0a3a8;
		--color-accent: #60a5fa;
		--color-accent-text: #0b1220;
		color-scheme: dark;
		position: fixed;
		inset: 0;
		z-index: 50;
		display: flex;
		background: #000;
		color: #fff;
		outline: none;
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
		gap: var(--space-1);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: inherit;
		cursor: pointer;
	}

	.icon:hover {
		background: rgb(255 255 255 / 0.12);
	}

	.icon:disabled {
		opacity: 0.4;
	}

	.live {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
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
</style>
