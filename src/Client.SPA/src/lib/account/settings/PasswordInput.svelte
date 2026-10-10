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
		class="icon-btn sm toggle"
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

	/* The shared input, with room on the right for the eye. */
	.password input {
		width: 100%;
		padding-right: calc(var(--control-h-sm) + var(--space-2));
	}

	.password input[aria-invalid='true'] {
		border-color: var(--color-danger);
	}

	.toggle {
		position: absolute;
		top: 50%;
		right: 2px;
		transform: translateY(-50%);
		color: var(--color-text-muted);
	}

	.toggle:hover {
		color: var(--color-text);
	}
</style>
