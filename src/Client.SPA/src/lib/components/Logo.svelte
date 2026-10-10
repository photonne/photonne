<script lang="ts">
	interface Props {
		/** Height of the mark in px; the wordmark scales with it. */
		size?: number;
		/** Only the sun, e.g. where space is tight. */
		markOnly?: boolean;
	}

	let { size = 28, markOnly = false }: Props = $props();
</script>

<!-- The brand of the native app (composeResources/drawable/photonne_logo_*):
     the sun mark and "Photo" + "nne" in gold. Drawn here so it follows the theme. -->
<span class="logo" style:--size="{size}px">
	<svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true">
		<rect width="64" height="64" rx="14" class="tile" />
		<path
			d="M32.00 14.00L32.00 5.50M47.59 23.00L54.95 18.75M47.59 41.00L54.95 45.25M32.00 50.00L32.00 58.50M16.41 41.00L9.05 45.25M16.41 23.00L9.05 18.75"
			class="rays"
		/>
		<circle cx="32" cy="32" r="13" class="ring" />
		<circle cx="32" cy="32" r="9.5" class="core" />
		<circle cx="29" cy="29" r="3.8" class="shine" />
	</svg>
	{#if !markOnly}<span class="word">Photo<span class="nne">nne</span></span>{/if}
</span>

<style>
	.logo {
		display: inline-flex;
		align-items: center;
		gap: calc(var(--size) * 0.3);
		line-height: 1;
	}

	svg {
		flex: none;
	}

	.tile {
		fill: var(--color-brand-tile);
	}

	.rays {
		stroke: var(--color-brand);
		stroke-width: 4.5;
		stroke-linecap: round;
	}

	.ring {
		fill: none;
		stroke: var(--color-brand-ring);
		stroke-width: 2;
	}

	.core {
		fill: var(--color-brand);
	}

	.shine {
		fill: #ffe9a8;
	}

	.word {
		font-size: calc(var(--size) * 0.72);
		font-weight: 800;
		letter-spacing: -0.01em;
		color: var(--color-text);
	}

	.nne {
		color: var(--color-brand);
	}
</style>
