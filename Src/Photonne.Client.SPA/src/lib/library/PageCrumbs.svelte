<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from './icons.js';

	interface Props {
		/** The page this one hangs from (a label's photos → Escenas → Explorar). */
		href: string;
		label: string;
		/**
		 * Brings its own page gutter: under a CollectionView's title. Inside a
		 * PageHeader toolbar the header already has it.
		 */
		inset?: boolean;
	}

	let { href, label, inset = false }: Props = $props();
</script>

<nav class="crumbs" class:inset aria-label={m.library_breadcrumb()}>
	<a {href}>
		<Icon path={icons.arrowBack} size={16} />
		{label}
	</a>
</nav>

<style>
	.crumbs {
		display: flex;
	}

	.inset {
		padding: var(--space-1) var(--page-gutter) 0;
	}

	/* A ghost .btn.sm, pulled left so its text lines up with the title. */
	a {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: var(--control-h-sm);
		margin-left: calc(-1 * var(--space-2));
		padding: 0 var(--space-2);
		border-radius: var(--radius-control);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		font-weight: 500;
		text-decoration: none;
	}

	a:hover {
		background: var(--color-hover);
		color: var(--color-text);
	}
</style>
