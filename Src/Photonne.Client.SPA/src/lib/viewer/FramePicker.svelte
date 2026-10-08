<script lang="ts">
	import { onMount } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { saveAssetMotionFrame } from '#lib/api/index.js';
	import { getAssetMotionFramesOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { clampFrame, frameUrl, nearestStripFrame, startFrame, stripFrames } from './frames.js';

	interface Props {
		assetId: string;
		onclose: () => void;
		/** The frame is now a photo of its own, with this id. */
		onsaved: (newAssetId: string) => void;
	}

	let { assetId, onclose, onsaved }: Props = $props();

	// The frames are JPEGs the server decodes from the clip, not a video, so
	// stepping is exact and works for every motion-photo format.
	const frames = createQuery(() => ({
		...getAssetMotionFramesOptions({ path: { assetId } }),
		retry: false
	}));
	const count = $derived(frames.data?.frameCount ?? 0);
	const strip = $derived(stripFrames(count));

	let index = $state(0);
	let started = false;
	let saving = $state(false);
	let failed = $state(false);
	let slider = $state<HTMLInputElement>();
	let closeButton = $state<HTMLButtonElement>();

	$effect(() => {
		if (count > 0 && !started) {
			started = true;
			index = startFrame(count);
		}
	});

	onMount(() => closeButton?.focus());

	function step(delta: number) {
		index = clampFrame(index + delta, count);
	}

	function onkeydown(event: KeyboardEvent) {
		// The slider already steps with the arrows; elsewhere they step too.
		if (event.target === slider) return;
		if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
			event.preventDefault();
			step(event.key === 'ArrowLeft' ? -1 : 1);
		}
	}

	async function save() {
		if (saving) return;
		saving = true;
		failed = false;
		const { data } = await saveAssetMotionFrame({ path: { assetId, index } });
		saving = false;
		if (data) onsaved(data.assetId);
		else failed = true;
	}
</script>

<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<section class="picker" aria-labelledby="frame-picker-title" {onkeydown}>
	<header>
		<button
			type="button"
			class="icon"
			aria-label={m.viewer_frame_close()}
			bind:this={closeButton}
			onclick={onclose}
		>
			<Icon name="close" />
		</button>
		<h2 id="frame-picker-title">{m.viewer_frame_title()}</h2>
	</header>

	<div class="preview">
		{#if frames.isPending}
			<p role="status">{m.session_restoring()}</p>
		{:else if frames.isError || count === 0}
			<p role="alert">{m.viewer_frame_load_error()}</p>
		{:else}
			<img
				src={frameUrl(assetId, index)}
				alt={m.viewer_frame_position({ index: index + 1, count })}
				draggable="false"
			/>
		{/if}
	</div>

	{#if count > 0}
		<div class="controls">
			<ul class="strip" aria-label={m.viewer_frame_strip()}>
				{#each strip as frame (frame)}
					<li>
						<button
							type="button"
							tabindex="-1"
							class:current={frame === nearestStripFrame(strip, index)}
							aria-label={m.viewer_frame_go({ index: frame + 1 })}
							onclick={() => (index = frame)}
						>
							<img src={frameUrl(assetId, frame)} alt="" loading="lazy" draggable="false" />
						</button>
					</li>
				{/each}
			</ul>
			<input
				type="range"
				min="0"
				max={count - 1}
				step="1"
				aria-label={m.viewer_frame_slider()}
				aria-valuetext={m.viewer_frame_position({ index: index + 1, count })}
				bind:value={index}
				bind:this={slider}
			/>
			<div class="stepper">
				<button
					type="button"
					class="icon"
					aria-label={m.viewer_frame_previous()}
					disabled={index <= 0}
					onclick={() => step(-1)}
				>
					<Icon name="chevronLeft" />
				</button>
				<span class="position" aria-live="polite">
					{m.viewer_frame_position({ index: index + 1, count })}
				</span>
				<button
					type="button"
					class="icon"
					aria-label={m.viewer_frame_next()}
					disabled={index >= count - 1}
					onclick={() => step(1)}
				>
					<Icon name="chevronRight" />
				</button>
			</div>
			<p class="hint">{m.viewer_frame_hint()}</p>
			{#if failed}
				<p class="error" role="alert">{m.viewer_frame_save_failed()}</p>
			{/if}
			<button type="button" class="save" disabled={saving} onclick={save}>
				{m.viewer_frame_save()}
			</button>
		</div>
	{/if}
</section>

<style>
	.picker {
		position: absolute;
		inset: 0;
		z-index: 3;
		display: flex;
		flex-direction: column;
		background: #000;
		color: #fff;
	}

	header {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-lg);
	}

	.preview {
		flex: 1;
		min-height: 0;
		display: grid;
		place-items: center;
		padding: var(--space-2);
	}

	.preview img {
		max-width: 100%;
		max-height: 100%;
		object-fit: contain;
	}

	.controls {
		display: grid;
		justify-items: center;
		gap: var(--space-2);
		padding: var(--space-3) var(--space-4) var(--space-4);
	}

	.strip {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		gap: 2px;
		max-width: 100%;
		overflow-x: auto;
	}

	.strip button {
		display: block;
		width: 64px;
		height: 48px;
		padding: 0;
		border: 2px solid transparent;
		border-radius: var(--radius-sm);
		overflow: hidden;
		background: #222;
		opacity: 0.6;
		cursor: pointer;
	}

	.strip button.current {
		border-color: var(--color-accent);
		opacity: 1;
	}

	.strip img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	input[type='range'] {
		width: min(100%, 660px);
		accent-color: var(--color-accent);
	}

	.stepper {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.position {
		min-width: 160px;
		text-align: center;
		font-variant-numeric: tabular-nums;
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

	.icon:hover:not(:disabled) {
		background: rgb(255 255 255 / 0.12);
	}

	.icon:disabled {
		opacity: 0.3;
		cursor: default;
	}

	.hint,
	.error {
		margin: 0;
		max-width: 52ch;
		text-align: center;
		font-size: var(--font-size-sm);
		color: rgb(255 255 255 / 0.7);
	}

	.error {
		color: var(--color-danger);
	}

	.save {
		padding: var(--space-2) var(--space-6);
		border: 0;
		border-radius: 999px;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
		cursor: pointer;
	}

	.save:disabled {
		opacity: 0.6;
		cursor: default;
	}
</style>
