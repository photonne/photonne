<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { filesFromDrop, isFileDrag, MEDIA_ACCEPT } from './files.js';
	import { ICON_FOLDER_UPLOAD } from '../icons.js';

	interface Props {
		/** Every file dropped or picked, folders walked (unfiltered). */
		onfiles: (files: File[]) => void;
		/**
		 * `area`: a box on the page with pickers. `window`: invisible until
		 * files are dragged anywhere over the window, then a full-screen target
		 * (for an app-wide drop zone).
		 */
		mode?: 'area' | 'window';
		/** Offer the folder picker (not on a guest upload). */
		folders?: boolean;
		disabled?: boolean;
		/** Replaces the default heading of the area. */
		title?: string;
		hint?: string;
	}

	let {
		onfiles,
		mode = 'area',
		folders = true,
		disabled = false,
		title = m.upload_drop_title(),
		hint = m.upload_drop_hint()
	}: Props = $props();

	let over = $state(false);
	// dragenter/dragleave fire for every child crossed; a depth count tells
	// when the pointer has really left.
	let depth = 0;
	let fileInput = $state<HTMLInputElement>();
	let folderInput = $state<HTMLInputElement>();

	function enter(event: DragEvent) {
		if (disabled || !isFileDrag(event)) return;
		event.preventDefault();
		depth++;
		over = true;
	}

	function overHandler(event: DragEvent) {
		if (disabled || !isFileDrag(event)) return;
		event.preventDefault();
		event.dataTransfer!.dropEffect = 'copy';
	}

	function leave(event: DragEvent) {
		if (!isFileDrag(event)) return;
		depth = Math.max(0, depth - 1);
		if (depth === 0) over = false;
	}

	async function drop(event: DragEvent) {
		if (disabled || !isFileDrag(event)) return;
		event.preventDefault();
		depth = 0;
		over = false;
		const files = await filesFromDrop(event.dataTransfer!);
		if (files.length) onfiles(files);
	}

	function picked(event: Event & { currentTarget: HTMLInputElement }) {
		const files = [...(event.currentTarget.files ?? [])];
		event.currentTarget.value = '';
		if (files.length) onfiles(files);
	}
</script>

<!-- An area zone on the page claims its drags first (preventDefault): the
     window zone leaves those alone so a drop is never queued twice. -->
<svelte:window
	ondragenter={(event) => mode === 'window' && !event.defaultPrevented && enter(event)}
	ondragover={(event) => mode === 'window' && !event.defaultPrevented && overHandler(event)}
	ondragleave={(event) => mode === 'window' && leave(event)}
	ondrop={(event) => mode === 'window' && !event.defaultPrevented && drop(event)}
/>

{#if mode === 'window'}
	{#if over}
		<div class="overlay" aria-hidden="true">
			<div class="overlay-card">
				<Icon name="upload" size={48} />
				<p>{m.upload_drop_release()}</p>
			</div>
		</div>
	{/if}
{:else}
	<section
		class="area"
		class:over
		class:disabled
		aria-label={title}
		ondragenter={enter}
		ondragover={overHandler}
		ondragleave={leave}
		ondrop={drop}
	>
		<span class="badge"><Icon name="upload" size={32} /></span>
		<h2>{over ? m.upload_drop_release() : title}</h2>
		<p>{hint}</p>
		<div class="buttons">
			<button type="button" class="primary" {disabled} onclick={() => fileInput?.click()}>
				<Icon name="add" size={18} />
				{m.upload_pick_files()}
			</button>
			{#if folders}
				<button type="button" {disabled} onclick={() => folderInput?.click()}>
					<Icon path={ICON_FOLDER_UPLOAD} size={18} />
					{m.upload_pick_folder()}
				</button>
			{/if}
		</div>
		<input
			bind:this={fileInput}
			type="file"
			multiple
			accept={MEDIA_ACCEPT}
			hidden
			tabindex="-1"
			onchange={picked}
		/>
		{#if folders}
			<input
				bind:this={folderInput}
				type="file"
				webkitdirectory
				hidden
				tabindex="-1"
				onchange={picked}
			/>
		{/if}
	</section>
{/if}

<style>
	.area {
		display: grid;
		justify-items: center;
		gap: var(--space-2);
		padding: var(--space-8) var(--space-6);
		border: 2px dashed var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface);
		text-align: center;
		transition:
			border-color var(--duration-fast),
			background var(--duration-fast);
	}

	.area.over {
		border-color: var(--color-accent);
		background: color-mix(in srgb, var(--color-accent) 10%, var(--color-surface));
	}

	.area.disabled {
		opacity: 0.6;
	}

	.badge {
		display: grid;
		place-items: center;
		width: 64px;
		height: 64px;
		border-radius: 50%;
		background: color-mix(in srgb, var(--color-accent) 14%, transparent);
		color: var(--color-accent);
	}

	h2 {
		margin: var(--space-2) 0 0;
		font-size: var(--font-size-lg);
	}

	p {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.buttons {
		display: flex;
		flex-wrap: wrap;
		justify-content: center;
		gap: var(--space-2);
		margin-top: var(--space-3);
	}

	button {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		cursor: pointer;
	}

	button.primary {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
	}

	button:disabled {
		opacity: 0.6;
		cursor: default;
	}

	.overlay {
		position: fixed;
		inset: 0;
		z-index: 90;
		display: grid;
		place-items: center;
		padding: var(--space-6);
		background: color-mix(in srgb, var(--color-accent) 18%, rgb(0 0 0 / 0.45));
		pointer-events: none;
	}

	.overlay-card {
		display: grid;
		justify-items: center;
		gap: var(--space-3);
		width: min(100%, 520px);
		padding: var(--space-8);
		border: 3px dashed var(--color-accent);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
		color: var(--color-accent);
		box-shadow: var(--shadow-raised);
	}

	.overlay-card p {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
		color: var(--color-text);
	}
</style>
