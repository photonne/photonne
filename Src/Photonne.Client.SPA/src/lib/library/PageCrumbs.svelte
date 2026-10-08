<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from './icons.js';

	/** The page this one hangs from (a label's photos → Escenas → Explorar). */
	let { href, label }: { href: string; label: string } = $props();
</script>

<nav class="crumbs" aria-label={m.library_breadcrumb()}>
	<a {href}>
		<Icon path={icons.arrowBack} size={16} />
		{label}
	</a>
</nav>

<style>
	.crumbs {
		padding: var(--space-1) var(--space-4) 0;
	}

	a {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		padding: var(--space-1) var(--space-2) var(--space-1) var(--space-1);
		border-radius: var(--radius-sm);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	a:hover {
		background: var(--color-surface);
		color: var(--color-text);
	}
</style>
