<script lang="ts">
	import type { BackgroundTaskResponse } from '#lib/api/index.js';
	import { m } from '#lib/paraglide/messages.js';
	import ProgressBar from './ProgressBar.svelte';
	import { statusLabel } from './labels.js';
	import { elapsedMs, isRunning, remainingMs } from './tasks.js';
	import { duration, localDateTime, percent, timeAgo } from './time.js';

	interface Props {
		/** The latest background task of this job, live progress applied. */
		task: BackgroundTaskResponse | undefined;
		/** What the job is doing while it runs ("Generando miniaturas"). */
		active: string;
		/** Asked to start, not yet seen running. */
		starting?: boolean;
		now: number;
	}

	let { task, active, starting = false, now }: Props = $props();

	const running = $derived(task !== undefined && isRunning(task));
	const left = $derived(task && running ? remainingMs(task, now) : null);
</script>

<div class="status" class:empty={!task && !starting} aria-live="polite">
	{#if task && running}
		<div class="line">
			<ProgressBar value={task.percentage} label={active} />
			<span class="pct">{percent(task.percentage)}</span>
		</div>
		<p class="meta">
			<strong>{active}</strong>
			{#if task.lastMessage}· <span class="msg" title={task.lastMessage}>{task.lastMessage}</span
				>{/if}
		</p>
		<p class="meta ops-muted">
			{m.ops_elapsed({ time: duration(elapsedMs(task, now)) })}
			{#if left !== null}· {m.ops_tasks_remaining({ time: duration(left) })}{/if}
		</p>
	{:else if starting}
		<div class="line"><ProgressBar value={null} label={m.ops_starting()} /></div>
		<p class="meta ops-muted">{m.ops_starting()}</p>
	{:else if task}
		<p class="meta">
			<span
				class="ops-badge"
				class:ok={task.status === 'Completed'}
				class:error={task.status === 'Failed'}
				class:warn={task.status === 'Cancelled'}>{statusLabel(task.status)}</span
			>
			<span class="ops-muted">
				{m.ops_last_run({ time: timeAgo(task.finishedAt ?? task.startedAt, now) })}
			</span>
			<span class="ops-muted" title={localDateTime(task.startedAt)}
				>· {duration(elapsedMs(task, now))}</span
			>
		</p>
		{#if task.lastMessage}
			<p class="meta result" title={task.lastMessage}>{task.lastMessage}</p>
		{/if}
	{/if}
</div>

<style>
	.status.empty {
		display: none;
	}

	.status {
		display: grid;
		gap: 2px;
		min-width: 0;
	}

	.line {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		margin-bottom: 2px;
	}

	.line :global([role='progressbar']) {
		flex: 1;
	}

	.pct {
		min-width: 3.5em;
		font-size: var(--font-size-sm);
		font-variant-numeric: tabular-nums;
		text-align: right;
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-1);
		margin: 0;
		font-size: var(--font-size-xs);
	}

	.msg,
	.result {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.msg {
		max-width: 60ch;
	}

	.result {
		display: block;
		color: var(--color-text-muted);
	}
</style>
