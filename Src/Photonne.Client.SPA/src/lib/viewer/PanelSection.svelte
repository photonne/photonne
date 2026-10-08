<script lang="ts" module>
	const KEY = 'photonne.viewer.collapsed';

	// Which sections the user folded, remembered across photos and visits.
	function readCollapsed(): string[] {
		try {
			const value = JSON.parse(localStorage.getItem(KEY) ?? '[]');
			return Array.isArray(value) ? value.filter((id) => typeof id === 'string') : [];
		} catch {
			return [];
		}
	}

	let collapsed = $state(readCollapsed());

	function setCollapsed(id: string, value: boolean) {
		collapsed = value
			? [...collapsed.filter((c) => c !== id), id]
			: collapsed.filter((c) => c !== id);
		try {
			localStorage.setItem(KEY, JSON.stringify(collapsed));
		} catch {
			// Remembered for this page only.
		}
	}
</script>

<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';

	interface Props {
		/** Stable id, for remembering whether it's folded. */
		id: string;
		title: string;
		children: Snippet;
		/** Small buttons next to the title (copy…), always reachable. */
		tools?: Snippet;
	}

	let { id, title, children, tools }: Props = $props();

	const bodyId = $props.id();
	const open = $derived(!collapsed.includes(id));
</script>

<section class="section">
	<div class="head">
		<h3>
			<button
				type="button"
				aria-expanded={open}
				aria-controls={bodyId}
				onclick={() => setCollapsed(id, open)}
			>
				<span class="chevron" class:open aria-hidden="true"
					><Icon name="chevronRight" size={18} /></span
				>
				{title}
			</button>
		</h3>
		{#if tools}<div class="tools">{@render tools()}</div>{/if}
	</div>
	<div id={bodyId} class="body" hidden={!open}>
		{@render children()}
	</div>
</section>

<style>
	.section {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: var(--space-2);
	}

	.head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	h3 {
		flex: 1;
		margin: 0;
	}

	h3 button {
		display: flex;
		align-items: center;
		gap: var(--space-1);
		width: 100%;
		margin-left: calc(-1 * var(--space-1));
		padding: 2px var(--space-1);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		font-weight: 600;
		text-align: left;
		cursor: pointer;
	}

	h3 button:hover {
		color: var(--color-text);
	}

	.chevron {
		display: grid;
		transition: transform var(--duration-fast);
	}

	.chevron.open {
		transform: rotate(90deg);
	}

	@media (prefers-reduced-motion: reduce) {
		.chevron {
			transition: none;
		}
	}

	.tools {
		display: flex;
		gap: var(--space-1);
	}

	.body {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: var(--space-2);
	}

	.body[hidden] {
		display: none;
	}
</style>
