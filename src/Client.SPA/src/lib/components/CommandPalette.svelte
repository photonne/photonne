<script lang="ts" module>
	import type { IconName } from './Icon.svelte';

	/** A place the palette leads to. */
	export interface PaletteEntry {
		href: string;
		label: string;
		/** The group it is listed under (Colecciones, Fijados…). */
		group?: string;
		icon?: IconName;
		iconPath?: string;
	}

	/** Lower case without accents: «álbumes» matches «albu». */
	export function fold(text: string) {
		return text
			.normalize('NFD')
			.replace(/\p{Diacritic}/gu, '')
			.toLowerCase();
	}
</script>

<script lang="ts">
	import { tick } from 'svelte';
	import { goto } from '$app/navigation';
	import { appHref as href } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import Icon from './Icon.svelte';

	/**
	 * Ctrl/Cmd + K from anywhere: a glass box to type where to go (any
	 * section, a pinned album or folder) or what to look for in the photos.
	 */
	let { entries }: { entries: PaletteEntry[] } = $props();

	let dialog = $state<HTMLDialogElement>();
	let input = $state<HTMLInputElement>();
	let query = $state('');
	let active = $state(0);

	const matches = $derived.by(() => {
		const words = fold(query.trim()).split(/\s+/).filter(Boolean);
		return entries.filter((entry) => {
			const text = fold(`${entry.label} ${entry.group ?? ''}`);
			return words.every((word) => text.includes(word));
		});
	});

	// What the list offers: searching the photos for the words first, then
	// the places that match them.
	const searchHref = $derived(
		query.trim() ? `${href('/search')}?q=${encodeURIComponent(query.trim())}` : null
	);
	const options = $derived([
		...(searchHref
			? [
					{
						href: searchHref,
						label: m.palette_search({ query: query.trim() }),
						icon: 'search' as const
					}
				]
			: []),
		...matches
	]);

	$effect(() => {
		void query;
		active = 0;
	});

	export async function open() {
		if (!dialog || dialog.open) return;
		query = '';
		dialog.showModal();
		await tick();
		input?.focus();
	}

	function close() {
		dialog?.close();
	}

	async function go(target: string) {
		close();
		await goto(target);
	}

	function onWindowKeydown(event: KeyboardEvent) {
		if (event.defaultPrevented || event.altKey || event.shiftKey) return;
		if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'k') return;
		event.preventDefault();
		if (dialog?.open) close();
		else open();
	}

	function onInputKeydown(event: KeyboardEvent) {
		if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
			event.preventDefault();
			const step = event.key === 'ArrowDown' ? 1 : -1;
			active = (active + step + options.length) % Math.max(options.length, 1);
			document.getElementById(`palette-option-${active}`)?.scrollIntoView({ block: 'nearest' });
		} else if (event.key === 'Enter') {
			event.preventDefault();
			const option = options[active];
			if (option) go(option.href);
		}
	}
</script>

<svelte:window onkeydown={onWindowKeydown} />

<!-- svelte-ignore a11y_click_events_have_key_events -->
<dialog
	bind:this={dialog}
	class="palette glass"
	aria-label={m.palette_title()}
	onclick={(event) => {
		// A click on the backdrop (the dialog itself, not its content) closes it.
		if (event.target === dialog) close();
	}}
>
	<div class="box">
		<label class="field">
			<Icon name="search" size={22} />
			<input
				bind:this={input}
				bind:value={query}
				class="bare"
				type="text"
				role="combobox"
				autocomplete="off"
				spellcheck="false"
				aria-label={m.palette_title()}
				aria-expanded="true"
				aria-controls="palette-list"
				aria-activedescendant={options.length ? `palette-option-${active}` : undefined}
				placeholder={m.palette_placeholder()}
				onkeydown={onInputKeydown}
			/>
			<kbd>Esc</kbd>
		</label>
		<ul id="palette-list" role="listbox" aria-label={m.palette_title()}>
			{#each options as option, index (option.href)}
				<li
					id="palette-option-{index}"
					role="option"
					aria-selected={index === active}
					onpointermove={() => (active = index)}
					onclick={() => go(option.href)}
				>
					<span class="glyph"><Icon name={option.icon} path={option.iconPath} size={20} /></span>
					<span class="label">{option.label}</span>
					{#if 'group' in option && option.group}<span class="group">{option.group}</span>{/if}
				</li>
			{/each}
		</ul>
		{#if query.trim() && matches.length === 0}
			<p class="empty">{m.palette_empty()}</p>
		{/if}
	</div>
</dialog>

<style>
	.palette {
		position: fixed;
		inset: 12vh 0 auto;
		width: min(640px, calc(100vw - 2 * var(--float-margin)));
		max-height: 70vh;
		margin: 0 auto;
		padding: 0;
		overflow: hidden;
		border-radius: var(--radius-xl);
		/* The glass layer is the background, not the UA's opaque canvas. */
		background: transparent;
		box-shadow: var(--shadow-nav);
	}

	.palette[open] {
		display: flex;
		animation: rise var(--duration-emphasis) var(--ease-spring);
	}

	/* A list to read, not a capsule to glance at: more frost than the chrome. */
	.palette::before {
		background: color-mix(in srgb, var(--chrome-base) 86%, transparent);
	}

	.palette::backdrop {
		background: rgb(10 10 12 / 0.35);
		backdrop-filter: blur(4px);
	}

	@keyframes rise {
		from {
			opacity: 0;
			transform: translateY(-8px) scale(0.97);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.palette[open] {
			animation: none;
		}
	}

	.box {
		display: flex;
		flex-direction: column;
		flex: 1;
		min-height: 0;
	}

	.field {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-4) var(--space-5);
		border-bottom: 1px solid color-mix(in srgb, currentColor 12%, transparent);
		color: var(--color-text-muted);
	}

	input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--color-text);
		font-size: var(--font-size-lg);
		outline: none;
	}

	kbd {
		padding: 0 var(--space-2);
		border: 1px solid color-mix(in srgb, currentColor 25%, transparent);
		border-radius: var(--radius-xs);
		font: inherit;
		font-size: var(--font-size-2xs);
		line-height: 20px;
	}

	ul {
		flex: 1;
		min-height: 0;
		margin: 0;
		padding: var(--space-2);
		overflow-y: auto;
		list-style: none;
	}

	li {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: 44px;
		padding: 0 var(--space-3);
		border-radius: var(--radius-pill);
		cursor: pointer;
	}

	li[aria-selected='true'] {
		background: var(--color-active-pill);
	}

	li[aria-selected='true'] .glyph {
		color: var(--color-accent);
	}

	.glyph {
		display: grid;
		color: var(--color-text-muted);
	}

	.label {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-weight: 500;
	}

	.group {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	.empty {
		margin: 0;
		padding: 0 var(--space-6) var(--space-4);
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}
</style>
