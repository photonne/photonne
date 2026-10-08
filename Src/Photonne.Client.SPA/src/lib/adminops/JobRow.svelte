<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { BackgroundTaskResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import TaskRow from './TaskRow.svelte';
	import TaskStatus from './TaskStatus.svelte';
	import type { MaintenanceTask, Options } from './catalog.js';
	import { icons } from './icons.js';
	import { isRunning } from './tasks.js';

	interface Props {
		task: MaintenanceTask;
		/** Its latest background task, live progress applied. */
		latest: BackgroundTaskResponse | undefined;
		options: Options;
		starting: boolean;
		stopping: boolean;
		now: number;
		onstart: () => void;
		onstop: (taskId: string) => void;
		extra?: Snippet;
	}

	let {
		task,
		latest,
		options = $bindable(),
		starting,
		stopping,
		now,
		onstart,
		onstop,
		extra
	}: Props = $props();

	const running = $derived(latest !== undefined && isRunning(latest));
</script>

<TaskRow
	id="task-{task.id}"
	icon={task.icon}
	title={task.title()}
	description={task.description()}
	danger={task.confirm !== undefined}
	active={running || starting}
>
	{#if task.options?.length}
		<div class="options" role="group" aria-label={m.ops_mt_options({ task: task.title() })}>
			{#each task.options as option (option.id)}
				{@const enabled = option.enabledWhen?.(options) ?? true}
				<label class="ops-check option">
					<input
						type="checkbox"
						bind:checked={options[option.id]}
						disabled={!enabled || running || starting}
					/>
					<span>
						{option.label()}
						{#if option.hint}<span class="hint">{option.hint()}</span>{/if}
					</span>
				</label>
			{/each}
		</div>
	{/if}

	<TaskStatus task={latest} active={task.active()} {starting} {now} />

	{@render extra?.()}

	{#snippet actions()}
		{#if task.href}
			<a class="ops-btn" href={appHref(task.href)}>
				<Icon path={icons.openInNew} size={18} />
				{m.ops_open()}
			</a>
		{:else if running && latest}
			<button
				type="button"
				class="ops-btn danger"
				disabled={stopping}
				onclick={() => onstop(latest.id)}
				aria-label="{m.ops_stop()}: {task.title()}"
			>
				<Icon path={icons.stop} size={18} />
				{m.ops_stop()}
			</button>
		{:else}
			<button
				type="button"
				class="ops-btn {task.confirm && !task.confirm.skip?.(options) ? 'danger' : 'primary'}"
				disabled={starting}
				onclick={onstart}
				aria-label="{m.ops_run()}: {task.title()}"
			>
				<Icon name="play" size={18} />
				{m.ops_run()}
			</button>
		{/if}
	{/snippet}
</TaskRow>

<style>
	.options {
		display: grid;
		gap: var(--space-1);
	}

	.option {
		align-items: flex-start;
	}

	.option input {
		margin-top: 3px;
	}

	.hint {
		display: block;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
