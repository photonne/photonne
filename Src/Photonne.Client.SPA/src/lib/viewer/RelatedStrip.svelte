<script lang="ts">
	import type { PersonAssetDto } from '#lib/api/index.js';
	import { longDate } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { asUtc } from './capture-date.js';

	interface Props {
		label: string;
		items: readonly PersonAssetDto[];
		onopen: (assetId: string) => void;
	}

	let { label, items, onopen }: Props = $props();
</script>

<ul class="strip" aria-label={label}>
	{#each items as item (item.id)}
		<li>
			<button
				type="button"
				title={item.fileName}
				aria-label={m.viewer_related_open({
					name: item.fileName,
					date: longDate(asUtc(item.fileCreatedAt))
				})}
				style:background-color={item.dominantColor}
				onclick={() => onopen(item.id)}
			>
				{#if item.hasThumbnails}
					<img src={thumbnailUrl(item.id, 'Small')} alt="" loading="lazy" draggable="false" />
				{/if}
				{#if item.type === 'Video'}
					<span class="video" aria-hidden="true">▶</span>
				{/if}
			</button>
		</li>
	{/each}
</ul>

<style>
	.strip {
		list-style: none;
		margin: 0;
		padding: 0 0 var(--space-1);
		display: flex;
		gap: var(--space-1);
		overflow-x: auto;
		scrollbar-width: thin;
	}

	li {
		flex: none;
	}

	button {
		position: relative;
		display: block;
		width: 64px;
		height: 64px;
		padding: 0;
		border: 0;
		border-radius: var(--radius-sm);
		overflow: hidden;
		background: var(--color-surface);
		cursor: pointer;
	}

	button:hover img {
		opacity: 0.85;
	}

	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.video {
		position: absolute;
		right: 4px;
		bottom: 2px;
		color: #fff;
		font-size: 10px;
		text-shadow: 0 0 3px #000;
	}
</style>
