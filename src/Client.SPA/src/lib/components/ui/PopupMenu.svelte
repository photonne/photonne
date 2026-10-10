<script lang="ts" module>
	import type { IconName } from '#lib/components/Icon.svelte';

	type Glyph = IconName | { path: string };

	interface Common {
		label: string;
		/** One of Icon's names, or a one-off 24×24 path. */
		icon?: Glyph;
		/** Shown at the right (e.g. "S"), also as aria-keyshortcuts when `keys` is unset. */
		shortcut?: string;
		/** aria-keyshortcuts, when it differs from the text shown ("Shift+F" for "⇧F"). */
		keys?: string;
		disabled?: boolean;
	}

	/**
	 * `action` runs; `check` is an on/off (menuitemcheckbox); `radio` is one
	 * of a set (menuitemradio); `link` is a plain link (a download…);
	 * `separator` splits groups.
	 */
	export type MenuEntry =
		| (Common & { kind?: 'action'; danger?: boolean; run: () => void })
		| (Common & { kind: 'check'; checked: boolean; run: () => void })
		| (Common & { kind: 'radio'; checked: boolean; run: () => void })
		| (Common & { kind: 'link'; href: string; download?: boolean })
		| { kind: 'separator'; id: string };
</script>

<script lang="ts">
	import { tick } from 'svelte';
	import Icon from '#lib/components/Icon.svelte';

	interface Props {
		/** The menu's name, and the trigger's when it has no visible text. */
		label: string;
		items: readonly MenuEntry[];
		/** The trigger's icon: "⋮" by default; with `text`, drawn before it when given. */
		icon?: Glyph | null;
		/** Visible text on the trigger (then `label` should contain it). */
		text?: string;
		/** Classes for the trigger: `icon-btn` by default, `btn sm` with text. */
		triggerClass?: string;
		title?: string;
		/** Which edge of the trigger the menu lines up with. */
		align?: 'start' | 'end';
		disabled?: boolean;
	}

	let {
		label,
		items,
		icon,
		text,
		triggerClass,
		title,
		align = 'end',
		disabled = false
	}: Props = $props();

	const id = $props.id();
	let open = $state(false);
	let trigger = $state<HTMLButtonElement>();
	let menu = $state<HTMLDivElement>();
	/** Where the menu opens, fixed to the window so no scroll box clips it. */
	let place = $state<{ top: number; left: number; up: boolean }>({ top: 0, left: 0, up: false });

	const GAP = 4;
	const MARGIN = 8;

	function position() {
		if (!trigger || !menu) return;
		const anchor = trigger.getBoundingClientRect();
		const { width, height } = menu.getBoundingClientRect();
		const below = window.innerHeight - anchor.bottom - GAP - MARGIN;
		const up = below < height && anchor.top - GAP - MARGIN > below;
		const preferred = align === 'start' ? anchor.left : anchor.right - width;
		place = {
			top: up ? anchor.top - GAP - height : anchor.bottom + GAP,
			left: Math.max(MARGIN, Math.min(preferred, window.innerWidth - width - MARGIN)),
			up
		};
	}

	const entries = () => [
		...(menu?.querySelectorAll<HTMLElement>('[role^="menuitem"]:not([aria-disabled="true"])') ?? [])
	];

	async function show(focus: 'first' | 'last' | 'checked' = 'first') {
		open = true;
		await tick();
		position();
		const all = entries();
		const checked = all.find((entry) => entry.getAttribute('aria-checked') === 'true');
		(focus === 'checked' && checked ? checked : focus === 'last' ? all.at(-1) : all[0])?.focus();
	}

	function hide(restoreFocus = true) {
		open = false;
		if (restoreFocus) trigger?.focus();
	}

	function onTriggerKey(event: KeyboardEvent) {
		if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
			event.preventDefault();
			event.stopPropagation();
			show(event.key === 'ArrowDown' ? 'first' : 'last');
		}
	}

	// The menu owns its keys while open: the page's own shortcuts (the
	// viewer's arrows, Escape…) wait until it closes. Tab leaves it.
	function onMenuKey(event: KeyboardEvent) {
		if (event.key === 'Tab') {
			hide(false);
			return;
		}
		event.stopPropagation();
		const all = entries();
		const index = all.indexOf(document.activeElement as HTMLElement);
		const move = (to: number) => all[(to + all.length) % all.length]?.focus();
		if (event.key === 'ArrowDown') move(index + 1);
		else if (event.key === 'ArrowUp') move(index - 1);
		else if (event.key === 'Home') move(0);
		else if (event.key === 'End') move(all.length - 1);
		else if (event.key === 'Escape') hide();
		else if (event.key.length === 1 && /\S/.test(event.key)) {
			// Type-ahead: the next item starting with that letter.
			const letter = event.key.toLowerCase();
			const order = [...all.slice(index + 1), ...all.slice(0, index + 1)];
			order.find((entry) => entry.textContent?.trim().toLowerCase().startsWith(letter))?.focus();
		} else return;
		event.preventDefault();
	}

	function choose(entry: MenuEntry) {
		if (entry.kind === 'separator' || entry.kind === 'link' || entry.disabled) return;
		hide();
		entry.run();
	}

	function onWindowPointer(event: PointerEvent) {
		if (!open) return;
		const target = event.target as Node;
		if (!menu?.contains(target) && !trigger?.contains(target)) hide(false);
	}

	const roles = { action: 'menuitem', check: 'menuitemcheckbox', radio: 'menuitemradio' };
</script>

<!-- A fixed menu would drift from its button: scrolling or resizing closes it. -->
<svelte:window
	onpointerdown={onWindowPointer}
	onresize={() => open && hide(false)}
	onscrollcapture={(event) => open && !menu?.contains(event.target as Node) && hide(false)}
/>

{#snippet glyph(value: Glyph | undefined | null, size: number)}
	{#if typeof value === 'string'}<Icon name={value} {size} />{:else if value}<Icon
			path={value.path}
			{size}
		/>{/if}
{/snippet}

{#snippet body(entry: Exclude<MenuEntry, { kind: 'separator' }>)}
	<span class="lead" aria-hidden="true">
		{#if entry.kind === 'radio' || entry.kind === 'check'}
			{#if entry.checked}<Icon name="check" size={18} />{:else}{@render glyph(entry.icon, 18)}{/if}
		{:else}
			{@render glyph(entry.icon, 18)}
		{/if}
	</span>
	<span class="text">{entry.label}</span>
	{#if entry.shortcut}<kbd aria-hidden="true">{entry.shortcut}</kbd>{/if}
{/snippet}

<div class="popup">
	<button
		bind:this={trigger}
		type="button"
		class={triggerClass ?? (text ? 'btn sm' : 'icon-btn')}
		class:open
		aria-haspopup="menu"
		aria-expanded={open}
		aria-controls={open ? `${id}-menu` : undefined}
		aria-label={label}
		title={title ?? (text ? undefined : label)}
		{disabled}
		onclick={() => (open ? hide() : show('checked'))}
		onkeydown={onTriggerKey}
	>
		{#if text}
			{#if icon}{@render glyph(icon, 18)}{/if}
			<span>{text}</span>
			<svg class="caret" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"
				><path d="M7 10l5 5 5-5z" fill="currentColor" /></svg
			>
		{:else}
			{@render glyph(icon ?? 'moreVert', 20)}
		{/if}
	</button>
	{#if open}
		<div
			bind:this={menu}
			id="{id}-menu"
			class="menu"
			class:up={place.up}
			style:top="{place.top}px"
			style:left="{place.left}px"
			role="menu"
			aria-label={label}
			tabindex="-1"
			onkeydown={onMenuKey}
		>
			{#each items as entry, index (entry.kind === 'separator' ? entry.id : `${index}:${entry.label}`)}
				{#if entry.kind === 'separator'}
					<div class="separator" role="separator"></div>
				{:else if entry.kind === 'link'}
					<a
						role="menuitem"
						tabindex="-1"
						href={entry.href}
						download={entry.download ? '' : undefined}
						aria-keyshortcuts={entry.keys ?? entry.shortcut}
						onclick={() => hide()}
					>
						{@render body(entry)}
					</a>
				{:else}
					<button
						type="button"
						role={roles[entry.kind ?? 'action']}
						tabindex="-1"
						class:danger={entry.kind !== 'check' && entry.kind !== 'radio' && entry.danger}
						aria-checked={entry.kind === 'check' || entry.kind === 'radio'
							? entry.checked
							: undefined}
						aria-disabled={entry.disabled ? 'true' : undefined}
						aria-keyshortcuts={entry.keys ?? entry.shortcut}
						onclick={() => choose(entry)}
					>
						{@render body(entry)}
					</button>
				{/if}
			{/each}
		</div>
	{/if}
</div>

<style>
	.popup {
		position: relative;
		display: inline-flex;
	}

	.caret {
		margin-right: calc(var(--space-1) * -1);
		color: var(--color-text-muted);
	}

	button.open:global(.icon-btn) {
		background: var(--color-hover);
	}

	.menu {
		position: fixed;
		z-index: 60;
		display: grid;
		min-width: 220px;
		padding: var(--space-1);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
		color: var(--color-text);
		animation: pop var(--duration-fast) ease-out;
	}

	.menu.up {
		animation-name: pop-up;
	}

	@keyframes pop {
		from {
			opacity: 0;
			transform: translateY(-4px);
		}
	}

	@keyframes pop-up {
		from {
			opacity: 0;
			transform: translateY(4px);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.menu {
			animation: none;
		}
	}

	[role^='menuitem'] {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: var(--control-h);
		padding: 0 var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		color: inherit;
		font-size: var(--font-size-sm);
		text-align: left;
		text-decoration: none;
		white-space: nowrap;
		cursor: pointer;
	}

	[role^='menuitem']:hover,
	[role^='menuitem']:focus-visible {
		background: var(--color-hover);
	}

	[role^='menuitem']:focus-visible {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	[role^='menuitem'][aria-checked='true'] {
		font-weight: 600;
	}

	[role^='menuitem'][aria-checked='true'] .lead {
		color: var(--color-accent);
	}

	[role^='menuitem'][aria-disabled='true'] {
		color: var(--color-text-muted);
		cursor: not-allowed;
	}

	[role^='menuitem'].danger {
		color: var(--color-danger);
	}

	.lead {
		display: grid;
		place-items: center;
		width: 20px;
		flex: none;
	}

	.text {
		flex: 1;
	}

	kbd {
		margin-left: var(--space-4);
		padding: 0 var(--space-1);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		color: var(--color-text-muted);
		font: inherit;
		font-size: var(--font-size-2xs);
		line-height: 18px;
	}

	.separator {
		height: 1px;
		margin: var(--space-1) 0;
		background: var(--color-border);
	}
</style>
