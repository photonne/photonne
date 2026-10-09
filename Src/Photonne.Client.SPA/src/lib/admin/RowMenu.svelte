<script lang="ts" module>
	import type { IconName } from '#lib/components/Icon.svelte';

	export interface RowMenuItem {
		label: string;
		icon?: IconName;
		/** A one-off 24×24 path, for icons outside the shared set. */
		iconPath?: string;
		/** Destructive: drawn in red, after a rule. */
		danger?: boolean;
		run: () => void;
	}
</script>

<script lang="ts">
	import { tick } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';

	/**
	 * A row's secondary actions behind a "⋮" button (the menu button pattern):
	 * arrows, Home and End move through the items, Escape closes and gives the
	 * focus back. The menu is fixed to the window, so a table's scroll box
	 * doesn't clip it.
	 */
	interface Props {
		/** The button's accessible name ("More actions for marta"). */
		label: string;
		items: readonly RowMenuItem[];
	}

	let { label, items }: Props = $props();

	const id = $props.id();
	let open = $state(false);
	let trigger = $state<HTMLButtonElement>();
	let menu = $state<HTMLDivElement>();
	let position = $state({ top: 0, right: 0, up: false });

	function entries() {
		return [...(menu?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])];
	}

	async function show(focus: 'first' | 'last' = 'first') {
		if (!trigger) return;
		const box = trigger.getBoundingClientRect();
		// Opens upwards when the row is near the bottom of the window.
		const up = window.innerHeight - box.bottom < 48 * items.length + 16;
		position = {
			top: up ? box.top - 4 : box.bottom + 4,
			right: window.innerWidth - box.right,
			up
		};
		open = true;
		await tick();
		const all = entries();
		(focus === 'first' ? all[0] : all.at(-1))?.focus();
	}

	function hide(restoreFocus = true) {
		if (!open) return;
		open = false;
		if (restoreFocus) trigger?.focus();
	}

	function onTriggerKey(event: KeyboardEvent) {
		if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
			event.preventDefault();
			show(event.key === 'ArrowDown' ? 'first' : 'last');
		}
	}

	function onMenuKey(event: KeyboardEvent) {
		const all = entries();
		const index = all.indexOf(document.activeElement as HTMLButtonElement);
		const move = (to: number) => {
			event.preventDefault();
			all[(to + all.length) % all.length]?.focus();
		};
		if (event.key === 'ArrowDown') move(index + 1);
		else if (event.key === 'ArrowUp') move(index - 1);
		else if (event.key === 'Home') move(0);
		else if (event.key === 'End') move(all.length - 1);
		else if (event.key === 'Escape') {
			event.preventDefault();
			event.stopPropagation();
			hide();
		} else if (event.key === 'Tab') hide(false);
	}

	function choose(item: RowMenuItem) {
		hide();
		item.run();
	}

	function onWindowPointer(event: PointerEvent) {
		if (!open) return;
		const target = event.target as Node;
		if (!menu?.contains(target) && !trigger?.contains(target)) hide(false);
	}
</script>

<svelte:window
	onpointerdown={onWindowPointer}
	onresize={() => hide(false)}
	onscrollcapture={(event) => {
		// The menu stays put while the page scrolls under it: close instead.
		if (open && !menu?.contains(event.target as Node)) hide(false);
	}}
/>

<button
	bind:this={trigger}
	type="button"
	class="icon-btn sm"
	aria-haspopup="menu"
	aria-expanded={open}
	aria-controls={open ? `${id}-menu` : undefined}
	aria-label={label}
	title={label}
	onclick={() => (open ? hide() : show())}
	onkeydown={onTriggerKey}
>
	<Icon name="moreVert" size={20} />
</button>
{#if open}
	<div
		bind:this={menu}
		id="{id}-menu"
		class="menu"
		class:up={position.up}
		style:top="{position.top}px"
		style:right="{position.right}px"
		role="menu"
		aria-label={label}
		tabindex="-1"
		onkeydown={onMenuKey}
	>
		{#each items as item (item.label)}
			<button
				type="button"
				role="menuitem"
				tabindex="-1"
				class:danger={item.danger}
				onclick={() => choose(item)}
			>
				<Icon name={item.icon} path={item.iconPath} size={18} />
				{item.label}
			</button>
		{/each}
	</div>
{/if}

<style>
	.icon-btn[aria-expanded='true'] {
		background: var(--color-hover);
	}

	.menu {
		position: fixed;
		z-index: 30;
		min-width: 220px;
		padding: var(--space-1);
		display: grid;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
		text-align: left;
	}

	.menu.up {
		transform: translateY(-100%);
	}

	[role='menuitem'] {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: var(--control-h);
		padding: 0 var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		color: var(--color-text);
		font-size: var(--font-size-sm);
		text-align: left;
		white-space: nowrap;
		cursor: pointer;
	}

	[role='menuitem'] :global(svg) {
		flex: none;
		color: var(--color-text-muted);
	}

	[role='menuitem']:hover,
	[role='menuitem']:focus-visible {
		background: var(--color-hover);
		outline: none;
	}

	[role='menuitem']:focus-visible {
		box-shadow: inset 0 0 0 2px var(--color-focus);
	}

	[role='menuitem'].danger,
	[role='menuitem'].danger :global(svg) {
		color: var(--color-danger);
	}

	/* The destructive items come last, after a rule. */
	[role='menuitem']:not(.danger) + .danger {
		margin-top: calc(var(--space-1) + 1px);
		position: relative;
	}

	[role='menuitem']:not(.danger) + .danger::before {
		content: '';
		position: absolute;
		left: var(--space-2);
		right: var(--space-2);
		top: calc(-1 * var(--space-1) / 2 - 1px);
		height: 1px;
		background: var(--color-border);
	}
</style>
