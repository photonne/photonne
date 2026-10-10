<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { faceThumbnailUrl, initials } from './people.js';

	interface Props {
		/** The face shown; without one, the initials (or a silhouette). */
		faceId: string | null | undefined;
		name?: string | null;
		/** CSS size; the crop is 220 px, sharp up to ~110 CSS px on a 2× screen. */
		size?: string;
		shape?: 'circle' | 'rounded';
	}

	let { faceId, name, size = '48px', shape = 'circle' }: Props = $props();

	let failed = $state<string | null>(null);
	const letters = $derived(initials(name));
</script>

<!-- Decorative: the person's name is always written next to it. -->
<span
	class="avatar {shape}"
	class:initials={!(faceId && failed !== faceId)}
	style:width={size}
	style:height={size}
	aria-hidden="true"
>
	{#if faceId && failed !== faceId}
		<img
			src={faceThumbnailUrl(faceId)}
			alt=""
			loading="lazy"
			decoding="async"
			draggable="false"
			onerror={() => (failed = faceId)}
		/>
	{:else if letters}
		<span class="letters">{letters}</span>
	{:else}
		<span class="silhouette"><Icon name="person" size={24} /></span>
	{/if}
</span>

<style>
	.avatar {
		display: grid;
		place-items: center;
		flex: none;
		overflow: hidden;
		container-type: size;
		background: var(--color-placeholder);
		color: var(--color-text-muted);
	}

	/* No face crop: the initials (or a silhouette) on the soft accent. */
	.initials {
		background: var(--color-accent-soft);
		color: var(--color-text);
	}

	.circle {
		border-radius: 50%;
	}

	.rounded {
		border-radius: var(--radius-md);
	}

	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.letters {
		font-weight: 600;
		font-size: 36cqw;
		letter-spacing: 0.02em;
	}

	.silhouette {
		display: grid;
		place-items: center;
		color: var(--color-accent);
	}

	.silhouette :global(svg) {
		width: 45cqw;
		height: 45cqw;
	}
</style>
