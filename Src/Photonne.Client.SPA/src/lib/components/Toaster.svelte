<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import Icon from './Icon.svelte';
	import { toasts, type Toast } from './toasts.svelte.js';

	async function run(toast: Toast) {
		toasts.dismiss(toast.id);
		await toast.action?.run();
	}
</script>

<div class="toaster" role="status" aria-live="polite">
	{#each toasts.items as toast (toast.id)}
		<div class="toast" class:error={toast.tone === 'error'}>
			<span>{toast.message}</span>
			{#if toast.action}
				<button type="button" class="action" onclick={() => run(toast)}>{toast.action.label}</button
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
	{/each}
</div>

<style>
	.toaster {
		position: fixed;
		left: 50%;
		bottom: var(--space-6);
		z-index: 100;
		transform: translateX(-50%);
		display: grid;
		gap: var(--space-2);
		justify-items: center;
		pointer-events: none;
	}

	.toast {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-width: 280px;
		max-width: min(560px, calc(100vw - 2 * var(--space-4)));
		padding: var(--space-2) var(--space-2) var(--space-2) var(--space-4);
		border-radius: var(--radius-md);
		background: #1d1d1f;
		color: #f4f4f5;
		box-shadow: var(--shadow-raised);
		pointer-events: auto;
	}

	.toast.error {
		background: #7f1d1d;
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
		padding: var(--space-1) var(--space-2);
		border-radius: var(--radius-sm);
		color: #93c5fd;
		font-weight: 600;
	}

	.close {
		display: grid;
		place-items: center;
		padding: var(--space-1);
		border-radius: 50%;
	}
</style>
