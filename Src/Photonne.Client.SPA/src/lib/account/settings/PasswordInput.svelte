<script lang="ts">
	import type { HTMLInputAttributes } from 'svelte/elements';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { ICON_VISIBILITY, ICON_VISIBILITY_OFF } from '../icons.js';

	interface Props extends Omit<HTMLInputAttributes, 'type' | 'value'> {
		value: string;
	}

	let { value = $bindable(''), ...rest }: Props = $props();
	let shown = $state(false);
</script>

<span class="password">
	<input {...rest} type={shown ? 'text' : 'password'} bind:value />
	<button
		type="button"
		class="toggle"
		aria-label={shown ? m.settings_password_hide() : m.settings_password_show()}
		aria-pressed={shown}
		onclick={() => (shown = !shown)}
	>
		<Icon path={shown ? ICON_VISIBILITY_OFF : ICON_VISIBILITY} size={18} />
	</button>
</span>

<style>
	.password {
		position: relative;
		display: block;
	}

	.password input {
		width: 100%;
		padding: var(--space-2) calc(var(--space-3) + 32px) var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		font-size: var(--font-size-md);
	}

	.password input[aria-invalid='true'] {
		border-color: var(--color-danger);
	}

	.toggle {
		position: absolute;
		top: 50%;
		right: 4px;
		transform: translateY(-50%);
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--color-text-muted);
		cursor: pointer;
	}

	.toggle:hover {
		color: var(--color-text);
	}
</style>
