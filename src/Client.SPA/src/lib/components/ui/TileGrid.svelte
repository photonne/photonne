<script lang="ts" module>
	import type { IconName } from '#lib/components/Icon.svelte';

	export interface Tile {
		href: string;
		label: string;
		icon?: IconName;
		/** A one-off SVG path, for icons outside the set (smart albums). */
		iconPath?: string;
		/** A count to show on the tile (unread notifications). */
		badge?: number;
	}
</script>

<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';

	/** A section of the «Colecciones» and «Más» tabs: a heading and its tiles. */
	let { title, tiles }: { title?: string; tiles: Tile[] } = $props();
</script>

<section>
	{#if title}<h2>{title}</h2>{/if}
	<ul>
		{#each tiles as tile (tile.href)}
			<li>
				<a href={tile.href}>
					<span class="glyph"
						><Icon name={tile.icon} path={tile.iconPath} size={22} />
						{#if tile.badge}<span class="badge">{tile.badge > 99 ? '99+' : tile.badge}</span
							>{/if}</span
					>
					<span class="label">{tile.label}</span>
				</a>
			</li>
		{/each}
	</ul>
</section>

<style>
	section {
		display: grid;
		gap: var(--space-3);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-xs);
		font-weight: 600;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--color-text-muted);
	}

	ul {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(min(160px, 40%), 1fr));
		gap: var(--space-3);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	a {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: 64px;
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
		color: inherit;
		font-size: var(--font-size-sm);
		font-weight: 600;
		text-decoration: none;
		transition:
			translate var(--duration-emphasis) var(--ease-spring),
			box-shadow var(--duration-normal);
	}

	a:hover {
		translate: 0 -2px;
		box-shadow: var(--shadow-bar);
	}

	.glyph {
		position: relative;
		display: grid;
		place-items: center;
		flex: none;
		width: 40px;
		height: 40px;
		border-radius: 50%;
		background: var(--color-accent-soft);
		color: var(--color-accent);
	}

	/* Long names wrap (and hyphenate) instead of being cut on a phone. */
	.label {
		flex: 1;
		min-width: 0;
		overflow-wrap: anywhere;
		hyphens: auto;
		line-height: 1.25;
	}

	.badge {
		position: absolute;
		top: -4px;
		right: -6px;
		min-width: 20px;
		padding: 0 6px;
		border-radius: var(--radius-pill);
		background: var(--color-badge);
		color: var(--color-badge-text);
		font-size: var(--font-size-2xs);
		font-weight: 700;
		line-height: 20px;
		text-align: center;
		box-shadow: 0 0 0 2px var(--color-surface-raised);
	}
</style>
