<script lang="ts">
	import type { SharedAssetDto } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { dateTime } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { IDENTITY, type ZoomState } from '#lib/viewer/zoom.js';
	import ZoomableImage from '#lib/viewer/ZoomableImage.svelte';
	import { isVideo, shareMediaUrl } from './share-api.js';

	interface Props {
		token: string;
		password: string | null;
		assets: readonly SharedAssetDto[];
		assetId: string;
		allowDownload: boolean;
		onnavigate: (assetId: string) => void;
		onclose: () => void;
	}

	let { token, password, assets, assetId, allowDownload, onnavigate, onclose }: Props = $props();

	let dialog = $state<HTMLDialogElement>();
	let image = $state<ZoomableImage>();
	let zoom = $state<ZoomState>(IDENTITY);

	const index = $derived(assets.findIndex((asset) => asset.id === assetId));
	const asset = $derived(index >= 0 ? assets[index] : null);
	const previous = $derived(index > 0 ? assets[index - 1] : null);
	const next = $derived(index >= 0 && index < assets.length - 1 ? assets[index + 1] : null);

	// A modal <dialog>: focus stays inside, the page behind is inert.
	$effect(() => {
		if (dialog && !dialog.open) dialog.showModal();
	});

	// Warm the cache for the neighbours, so arrowing through feels instant.
	$effect(() => {
		for (const neighbor of [previous, next]) {
			if (neighbor && !isVideo(neighbor)) {
				new Image().src = shareMediaUrl(token, neighbor.id, { thumbnail: 'Large' }, password);
			}
		}
	});

	/**
	 * Closes the modal right away (the page stops being inert, so the host
	 * can put focus back on the grid) and lets the host update the URL.
	 */
	function close() {
		dialog?.close();
		onclose();
	}

	function oncancel(event: Event) {
		// Escape: first back out of a zoom, then close.
		event.preventDefault();
		if (zoom.scale > 1) image?.reset();
		else close();
	}

	function onkeydown(event: KeyboardEvent) {
		if (event.ctrlKey || event.metaKey || event.altKey) return;
		if ((event.target as HTMLElement).closest('video')) return;
		const actions: Record<string, () => void> = {
			ArrowLeft: () => previous && zoom.scale === 1 && onnavigate(previous.id),
			ArrowRight: () => next && zoom.scale === 1 && onnavigate(next.id),
			'+': () => image?.zoomBy(1.5),
			'=': () => image?.zoomBy(1.5),
			'-': () => image?.zoomBy(1 / 1.5),
			'0': () => image?.reset()
		};
		const action = actions[event.key];
		if (action) {
			event.preventDefault();
			action();
		}
	}
</script>

<dialog
	class="viewer"
	bind:this={dialog}
	aria-label={asset?.fileName ?? m.viewer_title()}
	{oncancel}
	{onkeydown}
>
	{#if asset}
		<header class="bar">
			<button type="button" class="icon" aria-label={m.viewer_close()} onclick={close}>
				<Icon name="close" />
			</button>
			<div class="title">
				<span>{asset.fileName}</span>
				<span class="muted">
					{dateTime(asset.fileCreatedAt)} · {m.share_position({
						position: index + 1,
						total: assets.length
					})}
				</span>
			</div>
			{#if allowDownload}
				<a
					class="icon"
					href={shareMediaUrl(token, asset.id, { content: true, download: true }, password)}
					download={asset.fileName}
					aria-label={m.share_download_one({ name: asset.fileName })}
					title={m.viewer_download()}
				>
					<Icon name="download" />
				</a>
			{/if}
		</header>

		<div class="stage">
			{#key asset.id}
				{#if isVideo(asset)}
					<!-- svelte-ignore a11y_media_has_caption -->
					<video
						src={shareMediaUrl(token, asset.id, { content: true }, password)}
						poster={shareMediaUrl(token, asset.id, { thumbnail: 'Large' }, password)}
						controls
						autoplay
						playsinline
					></video>
				{:else}
					<ZoomableImage
						bind:this={image}
						bind:zoom
						previewSrc={shareMediaUrl(token, asset.id, { thumbnail: 'Large' }, password)}
						fullSrc={shareMediaUrl(token, asset.id, { content: true }, password)}
						alt={asset.fileName}
					/>
				{/if}
			{/key}
		</div>

		{#if previous}
			<button
				type="button"
				class="nav prev"
				aria-label={m.viewer_previous()}
				onclick={() => onnavigate(previous.id)}
			>
				<Icon name="chevronLeft" size={32} />
			</button>
		{/if}
		{#if next}
			<button
				type="button"
				class="nav next"
				aria-label={m.viewer_next()}
				onclick={() => onnavigate(next.id)}
			>
				<Icon name="chevronRight" size={32} />
			</button>
		{/if}
	{/if}
</dialog>

<style>
	/* Always dark, like the library viewer: the photo is the light. */
	.viewer {
		--color-bg: #111214;
		--color-surface: #26282c;
		--color-text: #e8e8ea;
		--color-text-muted: #a0a3a8;
		--color-focus: #93c5fd;
		color-scheme: dark;
		position: fixed;
		inset: 0;
		width: 100vw;
		height: 100vh;
		max-width: none;
		max-height: none;
		margin: 0;
		padding: 0;
		border: 0;
		background: #000;
		color: #fff;
	}

	.viewer::backdrop {
		background: #000;
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
		flex: 1;
		display: grid;
		min-width: 0;
		line-height: 1.3;
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

	.stage {
		position: absolute;
		inset: 0;
		display: grid;
		place-items: center;
	}

	video {
		max-width: 100%;
		max-height: 100%;
	}

	.nav {
		position: absolute;
		top: 50%;
		z-index: 2;
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
		transform: translateY(-50%);
	}

	.nav:hover {
		background: rgb(0 0 0 / 0.6);
	}

	.prev {
		left: var(--space-3);
	}

	.next {
		right: var(--space-3);
	}
</style>
