<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import Icon from './Icon.svelte';
	import { toasts, type Toast } from './toasts.svelte.js';

	async function run(toast: Toast) {
		toasts.dismiss(toast.id);
		await toast.action?.run();
	}

	let toaster = $state<HTMLDivElement>();
	/** Where the toaster goes back to when no modal dialog is open. */
	let home: Element | null = null;
	/** Modal dialogs in the order they opened: the last one is on top. */
	let modals: HTMLDialogElement[] = [];

	// The toaster is a manual popover, always open so its live region exists
	// before a message arrives: in the top layer it shows above the page, and
	// showing it again lifts it above whatever entered that layer since (a
	// dialog, the fullscreen viewer). A modal dialog also makes everything
	// outside it inert, popovers included, so while one is open the toaster
	// moves inside it and its buttons (Undo) still work.
	function place() {
		if (!toaster?.showPopover) return;
		modals = modals.filter((dialog) => dialog.isConnected && dialog.matches(':modal'));
		const host = modals.at(-1) ?? [...document.querySelectorAll('dialog:modal')].at(-1);
		// Moved as a whole and last in its parent: Svelte only updates what is inside it.
		const parent = host ?? home;
		if (parent && toaster.parentNode !== parent) parent.append(toaster);
		if (toaster.matches(':popover-open')) toaster.hidePopover();
		toaster.showPopover();
	}

	$effect(() => {
		if (!toaster) return;
		home ??= toaster.parentElement;
		place();
		// A dialog can leave the page while open (its component unmounts),
		// taking the toaster with it, with no close event: bring it back.
		const watcher = new MutationObserver(() => {
			if (toaster && !toaster.isConnected) place();
		});
		watcher.observe(document.body, { childList: true, subtree: true });
		return () => watcher.disconnect();
	});

	// Browsers without `toggle` on <dialog> catch up when a toast arrives.
	$effect(() => {
		if (toasts.items.length === 0 || !toaster) return;
		const inDialog = toaster.parentNode instanceof HTMLDialogElement;
		if (inDialog !== !!document.querySelector('dialog:modal')) place();
	});

	// Ctrl/Cmd+Z runs the Undo on screen, unless a field is taking the keys
	// (its own undo) or someone else already handled them.
	function onkeydown(event: KeyboardEvent) {
		if (event.defaultPrevented || event.shiftKey || event.altKey) return;
		if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'z') return;
		const target = event.target as HTMLElement | null;
		if (
			target?.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])')
		)
			return;
		if (toasts.undoLatest()) event.preventDefault();
	}

	function onToggle(event: Event) {
		const target = event.target;
		if (!(target instanceof HTMLDialogElement)) return;
		modals = modals.filter((dialog) => dialog !== target);
		if (target.matches(':modal')) modals.push(target);
		place();
	}
</script>

<!-- `toggle` and `close` don't bubble: captured, they report every dialog. -->
<svelte:document ontogglecapture={onToggle} onclosecapture={onToggle} onfullscreenchange={place} />

{#snippet item(toast: Toast)}
	<!-- Pointer or focus on a toast stops its countdown: there is time to read it and reach Undo. -->
	<div
		class="toast glass"
		class:error={toast.tone === 'error'}
		style:order={toast.id}
		role="group"
		onmouseenter={() => toasts.pause(toast.id)}
		onmouseleave={(event) => {
			if (!event.currentTarget.contains(document.activeElement)) toasts.resume(toast.id);
		}}
		onfocusin={() => toasts.pause(toast.id)}
		onfocusout={(event) => {
			const element = event.currentTarget;
			if (!element.contains(event.relatedTarget as Node | null) && !element.matches(':hover'))
				toasts.resume(toast.id);
		}}
	>
		<span>{toast.message}</span>
		{#if toast.action}
			<button
				type="button"
				class="action"
				aria-keyshortcuts={toast.action.label === m.action_undo() ? 'Control+Z Meta+Z' : undefined}
				onclick={() => run(toast)}>{toast.action.label}</button
			>
		{/if}
		<button
			type="button"
			class="close"
			aria-label={m.toast_dismiss()}
			onclick={() => toasts.dismiss(toast.id)}
		>
			<Icon name="close" size={16} />
		</button>
	</div>
{/snippet}

<svelte:window {onkeydown} />

<!-- Two live regions: errors interrupt (alert), the rest wait their turn
     (status). Both lay their toasts out in the toaster's own grid. -->
<div class="toaster" bind:this={toaster} popover="manual">
	<div class="region" role="status">
		{#each toasts.items.filter((toast) => toast.tone !== 'error') as toast (toast.id)}
			{@render item(toast)}
		{/each}
	</div>
	<div class="region" role="alert">
		{#each toasts.items.filter((toast) => toast.tone === 'error') as toast (toast.id)}
			{@render item(toast)}
		{/each}
	</div>
</div>

<style>
	/* Reset the popover's UA box (centred, bordered, opaque). At the top, as
	   in the native app: the bottom belongs to the navigation and the action
	   capsules. */
	.toaster {
		position: fixed;
		inset: calc(env(safe-area-inset-top, 0px) + var(--float-margin)) auto auto 50%;
		z-index: 100;
		transform: translateX(-50%);
		margin: 0;
		padding: 0;
		border: 0;
		background: transparent;
		overflow: visible;
		color: inherit;
	}

	.toaster:popover-open {
		display: grid;
		gap: var(--space-2);
		justify-items: center;
		pointer-events: none;
	}

	.region {
		display: contents;
	}

	.toast {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-width: 280px;
		max-width: min(560px, calc(100vw - 2 * var(--space-4)));
		min-height: 48px;
		padding: var(--space-1) var(--space-1) var(--space-1) var(--space-4);
		border-radius: 24px;
		box-shadow: var(--shadow-bar);
		font-size: var(--font-size-sm);
		font-weight: 500;
		pointer-events: auto;
		transition:
			opacity var(--duration-emphasis) ease,
			translate var(--duration-chrome) var(--ease-spring),
			scale var(--duration-chrome) var(--ease-spring);
	}

	/* Slides down from above as it arrives. */
	@starting-style {
		.toast {
			opacity: 0;
			translate: 0 -16px;
			scale: 0.96;
		}
	}

	/* An error stays solid: it has to read over anything. */
	.toast.error {
		border-color: transparent;
		background: var(--color-toast-danger);
		color: var(--color-toast-text);
		-webkit-backdrop-filter: none;
		backdrop-filter: none;
	}

	span {
		flex: 1;
	}

	button {
		border: 0;
		background: transparent;
		color: inherit;
		cursor: pointer;
	}

	.action {
		min-height: var(--control-h-sm);
		padding: 0 var(--space-4);
		border-radius: var(--radius-pill);
		background: var(--color-active-pill);
		color: var(--color-accent);
		font-weight: 700;
	}

	.toast.error .action {
		color: var(--color-toast-action);
	}

	.close {
		display: grid;
		place-items: center;
		width: var(--control-h);
		height: var(--control-h);
		border-radius: 50%;
	}

	.close:hover,
	.action:hover {
		background: var(--color-hover);
	}
</style>
