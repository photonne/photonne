<script lang="ts">
	import type { FaceDto } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import { faceBox } from './faces.js';
	import PersonName from './PersonName.svelte';

	interface Props {
		faces: readonly FaceDto[];
		/** The face picked here or in the panel's list, drawn highlighted. */
		selectedId: string | null;
		/** Zoom scale of the image: strokes and labels keep their size on screen. */
		scale: number;
		onselect: (faceId: string) => void;
	}

	let { faces, selectedId, scale, onselect }: Props = $props();
</script>

<ul class="boxes" style:--zoom-scale={scale} aria-label={m.viewer_faces_boxes()}>
	{#each faces as face (face.id)}
		{@const box = faceBox(face)}
		<li style:left={box.left} style:top={box.top} style:width={box.width} style:height={box.height}>
			<button
				type="button"
				class="box"
				class:selected={face.id === selectedId}
				class:unknown={!face.personId}
				aria-pressed={face.id === selectedId}
				onclick={() => onselect(face.id)}
				ondblclick={(event) => event.stopPropagation()}
			>
				<span class="visually-hidden">{m.viewer_face_box()}</span>
				<span class="tag">
					{#if face.personId}<PersonName
							id={face.personId}
						/>{:else}{m.viewer_face_unassigned()}{/if}
				</span>
			</button>
		</li>
	{/each}
</ul>

<style>
	.boxes {
		position: absolute;
		inset: 0;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	li {
		position: absolute;
	}

	.box {
		position: absolute;
		inset: 0;
		padding: 0;
		border: calc(2px / var(--zoom-scale)) solid rgb(255 255 255 / 0.9);
		border-radius: calc(6px / var(--zoom-scale));
		background: transparent;
		box-shadow: 0 0 0 calc(1px / var(--zoom-scale)) rgb(0 0 0 / 0.5);
		pointer-events: auto;
		cursor: pointer;
	}

	.box.unknown {
		border-style: dashed;
	}

	.box:hover,
	.box:focus-visible,
	.box.selected {
		border-color: var(--color-accent);
		outline: none;
	}

	.tag {
		position: absolute;
		top: 100%;
		left: 50%;
		margin-top: calc(4px / var(--zoom-scale));
		padding: 2px 8px;
		border-radius: 999px;
		background: rgb(0 0 0 / 0.7);
		color: #fff;
		font-size: var(--font-size-xs);
		white-space: nowrap;
		/* Back to screen size whatever the zoom, from the top centre of the tag. */
		transform: translateX(-50%) scale(calc(1 / var(--zoom-scale)));
		transform-origin: top center;
	}

	.box.selected .tag {
		background: var(--color-accent);
		color: var(--color-accent-text);
	}
</style>
