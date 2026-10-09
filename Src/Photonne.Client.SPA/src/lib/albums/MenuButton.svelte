<script lang="ts" module>
	import type { IconName } from '#lib/components/Icon.svelte';

	export interface MenuItem {
		label: string;
		/** One of Icon's names, or a one-off 24×24 path (icons.ts). */
		icon?: IconName | { path: string };
		danger?: boolean;
		run: () => void;
	}
</script>

<script lang="ts">
	import { tick } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';

	interface Props {
		label: string;
		items: readonly MenuItem[];
		/** The trigger's icon; a "more" icon by default. */
		icon?: IconName;
		/** Show the label next to the icon instead of only as its accessible name. */
		showLabel?: boolean;
	}

	let { label, items, icon = 'moreVert', showLabel = false }: Props = $props();

	const id = $props.id();
	let open = $state(false);
	let trigger = $state<HTMLButtonElement>();
	let menu = $state<HTMLDivElement>();

	function entries() {
		return [...(menu?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])];
	}

	async function show(focus: 'first' | 'last' = 'first') {
		open = true;
		await tick();
		const all = entries();
		(focus === 'first' ? all[0] : all.at(-1))?.focus();
	}

	function hide(restoreFocus = true) {
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

	function choose(item: MenuItem) {
		hide();
		item.run();
	}

	function onWindowPointer(event: PointerEvent) {
		if (!open) return;
		const target = event.target as Node;
		if (!menu?.contains(target) && !trigger?.contains(target)) hide(false);
	}
</script>

<svelte:window onpointerdown={onWindowPointer} />

<div class="menu-button">
	<button
		bind:this={trigger}
		type="button"
		class={showLabel ? 'btn trigger' : 'icon-btn trigger'}
		aria-haspopup="menu"
		aria-expanded={open}
		aria-controls={open ? `${id}-menu` : undefined}
		aria-label={showLabel ? undefined : label}
		title={showLabel ? undefined : label}
		onclick={() => (open ? hide() : show())}
		onkeydown={onTriggerKey}
	>
		<Icon name={icon} />
		{#if showLabel}<span>{label}</span>{/if}
	</button>
	{#if open}
		<div
			bind:this={menu}
			id="{id}-menu"
			class="menu"
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
					{#if typeof item.icon === 'string'}
						<Icon name={item.icon} size={18} />
					{:else if item.icon}
						<Icon path={item.icon.path} size={18} />
					{/if}
					{item.label}
				</button>
			{/each}
		</div>
	{/if}
</div>

<style>
	.menu-button {
		position: relative;
	}

	.trigger[aria-expanded='true'] {
		background: var(--color-hover);
	}

	.menu {
		position: absolute;
		right: 0;
		top: calc(100% + var(--space-1));
		z-index: 20;
		min-width: 220px;
		padding: var(--space-1);
		display: grid;
		background: var(--color-surface-raised);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-raised);
	}

	[role='menuitem'] {
		display: flex;
		min-height: var(--control-h);
		color: var(--color-text);
		font-size: var(--font-size-sm);
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		text-align: left;
		white-space: nowrap;
		cursor: pointer;
	}

	[role='menuitem']:hover,
	[role='menuitem']:focus-visible {
		background: var(--color-hover);
		outline: none;
	}

	[role='menuitem']:focus-visible {
		box-shadow: inset 0 0 0 2px var(--color-focus);
	}

	[role='menuitem'].danger {
		color: var(--color-danger);
	}
</style>
