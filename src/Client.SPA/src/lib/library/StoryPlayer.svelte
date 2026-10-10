<script lang="ts">
	import { onMount } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { longDate } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { GridAsset } from '#lib/timeline/types.js';
	import { icons } from './icons.js';
	import { goTo, segmentFill, tick, type StoryPosition } from './story.js';

	interface Props {
		title: string;
		subtitle?: string | null;
		items: readonly GridAsset[];
		/** Where to start (the photo the user had focused, or the first). */
		start?: number;
		onclose: () => void;
	}

	let { title, subtitle = null, items, start = 0, onclose }: Props = $props();

	// Past this many photos the segments get too thin to read; one bar instead.
	const MAX_SEGMENTS = 40;

	// Starts where it's asked to, then the user drives it.
	let position = $state<StoryPosition>(goTo(0, 0));
	let paused = $state(false);
	let root = $state<HTMLDivElement>();
	let pauseButton = $state<HTMLButtonElement>();

	const current = $derived(items[position.index]);
	const segments = $derived(items.length <= MAX_SEGMENTS ? items.length : 1);
	const overallProgress = $derived(
		items.length ? (position.index + position.progress) / items.length : 0
	);

	onMount(() => {
		position = goTo(start, items.length);
		const opener = document.activeElement as HTMLElement | null;
		pauseButton?.focus();
		const overflow = document.body.style.overflow;
		document.body.style.overflow = 'hidden';

		let last = performance.now();
		let frame = requestAnimationFrame(function step(now) {
			const delta = now - last;
			last = now;
			// Videos move on their own clock (timeupdate / ended).
			if (!paused && current && !current.isVideo) position = tick(position, delta, items.length);
			frame = requestAnimationFrame(step);
		});

		return () => {
			cancelAnimationFrame(frame);
			document.body.style.overflow = overflow;
			opener?.focus();
		};
	});

	// Warm the next photo so the cut is instant.
	$effect(() => {
		const next = items[position.index + 1];
		if (next && !next.isVideo) new Image().src = imageUrl(next);
	});

	function imageUrl(item: GridAsset) {
		return thumbnailUrl(item.id, 'Large', item.thumbnailVersion);
	}

	function go(index: number) {
		position = goTo(index, items.length);
	}

	function togglePause() {
		if (position.finished) {
			go(0);
			paused = false;
		} else {
			paused = !paused;
		}
	}

	// Captured at the window and kept here: the page behind (a selection's
	// shortcuts, the grid) must not react while the story is up.
	function onkeydowncapture(event: KeyboardEvent) {
		event.stopPropagation();
		if (event.key === 'Tab') return trapFocus(event);
		if (event.ctrlKey || event.metaKey || event.altKey) return;
		// Space on a focused button presses that button.
		if (event.key === ' ' && (event.target as HTMLElement).closest('button')) return;
		const actions: Record<string, () => void> = {
			Escape: onclose,
			ArrowLeft: () => go(position.index - 1),
			ArrowRight: () => go(position.index + 1),
			' ': togglePause,
			k: togglePause,
			Home: () => go(0),
			End: () => go(items.length - 1)
		};
		const action = actions[event.key];
		if (!action) return;
		event.preventDefault();
		action();
	}

	function trapFocus(event: KeyboardEvent) {
		if (!root) return;
		const focusable = [...root.querySelectorAll<HTMLElement>('button:not([disabled])')];
		const first = focusable[0];
		const last = focusable.at(-1);
		if (event.shiftKey && document.activeElement === first) {
			event.preventDefault();
			last?.focus();
		} else if (!event.shiftKey && document.activeElement === last) {
			event.preventDefault();
			first?.focus();
		}
	}

	function onvideotime(event: Event) {
		const video = event.currentTarget as HTMLVideoElement;
		if (video.duration) position = { ...position, progress: video.currentTime / video.duration };
	}

	function onvideoended() {
		if (position.index < items.length - 1) go(position.index + 1);
		else position = { ...position, progress: 1, finished: true };
	}

	// A paused story pauses its video too.
	function playback(video: HTMLVideoElement, isPaused: boolean) {
		const apply = (value: boolean) => void (value ? video.pause() : video.play().catch(() => {}));
		apply(isPaused);
		return { update: apply };
	}
</script>

<svelte:window {onkeydowncapture} />

<div
	class="story"
	role="dialog"
	aria-modal="true"
	aria-label={title}
	tabindex="-1"
	bind:this={root}
>
	<div class="progress" aria-hidden="true">
		{#if segments > 1}
			{#each items as item, segment (item.id)}
				<span class="segment">
					<span class="fill" style:transform="scaleX({segmentFill(segment, position)})"></span>
				</span>
			{/each}
		{:else}
			<span class="segment">
				<span class="fill" style:transform="scaleX({overallProgress})"></span>
			</span>
		{/if}
	</div>

	<header class="bar">
		<div class="title">
			<span class="name">{title}</span>
			{#if current}
				<span class="meta">
					{subtitle ? `${subtitle} · ` : ''}{longDate(current.capturedAt)}
				</span>
			{/if}
		</div>
		<span class="counter">
			{m.memories_story_position({ index: position.index + 1, count: items.length })}
		</span>
		<button
			type="button"
			class="icon"
			bind:this={pauseButton}
			aria-label={position.finished
				? m.memories_story_replay()
				: paused
					? m.memories_story_play()
					: m.memories_story_pause()}
			onclick={togglePause}
		>
			<Icon path={paused || position.finished ? icons.play : icons.pause} />
		</button>
		<button type="button" class="icon" aria-label={m.viewer_close()} onclick={onclose}>
			<Icon name="close" />
		</button>
	</header>

	<div class="stage">
		{#if current}
			{#key current.id}
				{#if current.isVideo}
					<video
						src="/api/assets/{current.id}/content"
						poster={imageUrl(current)}
						muted
						playsinline
						use:playback={paused}
						ontimeupdate={onvideotime}
						onended={onvideoended}
					></video>
				{:else}
					<img
						class="moving"
						src={imageUrl(current)}
						alt={current.fileName}
						style:animation-play-state={paused ? 'paused' : 'running'}
					/>
				{/if}
			{/key}
		{/if}

		<button
			type="button"
			class="zone previous"
			aria-label={m.viewer_previous()}
			disabled={position.index === 0}
			onclick={() => go(position.index - 1)}
		>
			<Icon name="chevronLeft" size={32} />
		</button>
		<button
			type="button"
			class="zone next"
			aria-label={m.viewer_next()}
			disabled={position.index >= items.length - 1}
			onclick={() => go(position.index + 1)}
		>
			<Icon name="chevronRight" size={32} />
		</button>
	</div>
</div>

<style>
	.story {
		position: fixed;
		inset: 0;
		z-index: 60;
		display: flex;
		flex-direction: column;
		background: #000;
		color: #fff;
		outline: none;
	}

	.progress {
		display: flex;
		gap: 4px;
		padding: var(--space-3) var(--space-4) 0;
	}

	.segment {
		flex: 1;
		height: 3px;
		overflow: hidden;
		border-radius: 2px;
		background: rgb(255 255 255 / 0.3);
	}

	.fill {
		display: block;
		height: 100%;
		background: #fff;
		transform-origin: left;
	}

	.bar {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-4);
	}

	.title {
		display: grid;
		min-width: 0;
		margin-right: auto;
	}

	.name {
		font-weight: 600;
		font-size: var(--font-size-lg);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta,
	.counter {
		font-size: var(--font-size-sm);
		color: rgb(255 255 255 / 0.75);
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
		position: relative;
		flex: 1;
		min-height: 0;
		display: grid;
		place-items: center;
		overflow: hidden;
	}

	img,
	video {
		max-width: 100%;
		max-height: 100%;
		object-fit: contain;
	}

	img {
		width: 100%;
		height: 100%;
	}

	/* A slow push-in over the photo's five seconds. */
	img.moving {
		animation: push 5s linear both;
	}

	@keyframes push {
		from {
			transform: scale(1);
		}
		to {
			transform: scale(1.06);
		}
	}

	.zone {
		position: absolute;
		top: 0;
		bottom: 0;
		display: flex;
		align-items: center;
		width: 30%;
		padding: 0 var(--space-4);
		border: 0;
		background: transparent;
		color: #fff;
		cursor: pointer;
		opacity: 0;
		transition: opacity var(--duration-normal);
	}

	.zone:hover:not(:disabled),
	.zone:focus-visible {
		opacity: 1;
	}

	.zone:disabled {
		cursor: default;
	}

	.previous {
		left: 0;
	}

	.next {
		right: 0;
		justify-content: flex-end;
	}

	@media (prefers-reduced-motion: reduce) {
		img.moving {
			animation: none;
		}
	}
</style>
