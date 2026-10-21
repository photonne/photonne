<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon, { type IconName } from '#lib/components/Icon.svelte';

	interface Props {
		/** The button's name, and the panel's. */
		label: string;
		icon?: IconName;
		/** A one-off SVG path for an icon outside the set. */
		iconPath?: string;
		/** Something in it is not at its default: the button shows a dot. */
		active?: boolean;
		children: Snippet;
	}

	let { label, icon, iconPath, active = false, children }: Props = $props();

	const id = `chrome-menu-${Math.random().toString(36).slice(2, 9)}`;
	let trigger = $state<HTMLButtonElement>();
	let panel = $state<HTMLDivElement>();
	let open = $state(false);
	let place = $state({ top: 0, right: 0 });

	// A popover (top layer, light dismiss, Escape) under its button, right
	// edges aligned; on a phone the stylesheet makes it a sheet instead.
	function ontoggle(event: ToggleEvent) {
		open = event.newState === 'open';
		if (!open || !trigger) return;
		const box = trigger.getBoundingClientRect();
		place = { top: box.bottom + 8, right: Math.max(8, window.innerWidth - box.right) };
		panel?.querySelector<HTMLElement>('input, button, select, [tabindex]')?.focus();
	}

	export function close() {
		panel?.hidePopover();
	}
</script>

<button
	bind:this={trigger}
	type="button"
	class="icon-btn trigger"
	class:open
	popovertarget={id}
	aria-label={label}
	title={label}
	aria-expanded={open}
	aria-haspopup="dialog"
>
	<Icon name={icon} path={iconPath} />
	{#if active}<span class="dot" aria-hidden="true"></span>{/if}
</button>

<div
	bind:this={panel}
	{id}
	class="menu glass"
	popover="auto"
	role="dialog"
	aria-label={label}
	style:--top="{place.top}px"
	style:--right="{place.right}px"
	{ontoggle}
>
	<h2 class="title">{label}</h2>
	{@render children()}
</div>

<style>
	.trigger {
		position: relative;
	}

	.trigger.open {
		background: var(--color-active-pill);
		color: var(--color-accent);
	}

	.dot {
		position: absolute;
		top: 6px;
		right: 6px;
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--color-primary-fill);
		box-shadow: 0 0 0 2px var(--chrome-base);
	}

	/* Reset the popover's UA box (centred, bordered, opaque). */
	.menu {
		--glass-tint: 92%;
		position: fixed;
		inset: var(--top) var(--right) auto auto;
		width: min(320px, calc(100vw - 2 * var(--float-margin)));
		max-height: calc(100dvh - var(--top) - var(--float-margin));
		margin: 0;
		padding: var(--space-4);
		overflow-y: auto;
		border-radius: var(--radius-lg);
		background: transparent;
		box-shadow: var(--shadow-nav);
		color: var(--color-text);
	}

	.menu:popover-open {
		display: grid;
		gap: var(--space-3);
		animation: pop var(--duration-emphasis) var(--ease-spring);
		transform-origin: top right;
	}

	@keyframes pop {
		from {
			opacity: 0;
			transform: scale(0.94);
		}
	}

	.title {
		margin: 0;
		font-size: var(--font-size-sm);
		font-weight: 650;
	}

	/* A phone: a sheet from the bottom edge, with its grip, as the native
	   app's bottom sheets. */
	@media (max-width: 700px) {
		.menu {
			inset: auto 0 0 0;
			width: auto;
			max-height: 80dvh;
			padding: var(--space-6) var(--space-5) calc(var(--space-6) + env(safe-area-inset-bottom, 0px));
			border-width: 1px 0 0;
			border-radius: var(--radius-xl) var(--radius-xl) 0 0;
		}

		.menu:popover-open {
			animation-name: sheet;
		}

		.menu::after {
			content: '';
			position: absolute;
			top: var(--space-2);
			left: 50%;
			width: 36px;
			height: 4px;
			border-radius: var(--radius-pill);
			background: var(--color-border-strong);
			translate: -50% 0;
		}

		.menu::backdrop {
			background: rgb(0 0 0 / 0.3);
		}

		.title {
			font-size: var(--font-size-md);
		}
	}

	@keyframes sheet {
		from {
			transform: translateY(100%);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.menu:popover-open {
			animation: none;
		}
	}
</style>
