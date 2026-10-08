<script lang="ts">
	interface Props {
		/** 0–100; null draws an indeterminate bar. */
		value: number | null;
		label: string;
		tone?: 'accent' | 'danger';
	}

	let { value, label, tone = 'accent' }: Props = $props();
</script>

<div
	class="bar"
	class:indeterminate={value === null}
	class:danger={tone === 'danger'}
	role="progressbar"
	aria-label={label}
	aria-valuemin={0}
	aria-valuemax={100}
	aria-valuenow={value === null ? undefined : Math.round(value)}
>
	<div
		class="fill"
		style:width={value === null ? undefined : `${Math.min(100, Math.max(0, value))}%`}
	></div>
</div>

<style>
	.bar {
		position: relative;
		height: 6px;
		overflow: hidden;
		border-radius: 999px;
		background: var(--color-surface);
		box-shadow: inset 0 0 0 1px var(--color-border);
	}

	.fill {
		height: 100%;
		border-radius: inherit;
		background: var(--color-accent);
		transition: width var(--duration-normal) ease-out;
	}

	.danger .fill {
		background: var(--color-danger);
	}

	.indeterminate .fill {
		position: absolute;
		width: 35%;
		animation: slide 1.4s ease-in-out infinite;
	}

	@keyframes slide {
		from {
			left: -35%;
		}
		to {
			left: 100%;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.indeterminate .fill {
			left: 0;
			width: 100%;
			opacity: 0.4;
			animation: none;
		}
	}
</style>
