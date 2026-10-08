<script lang="ts">
	import {
		createExternalLibrary,
		updateExternalLibrary,
		type ExternalLibraryDto
	} from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { errorText } from './errors.js';
	import { cronOf, SCHEDULES, scheduleOf, type Schedule } from './libraries.js';

	interface Props {
		open: boolean;
		/** The library to edit; null creates one. */
		library: ExternalLibraryDto | null;
		onclose: () => void;
		/** `scanNow`: the admin asked to scan the new library right away. */
		onsaved: (library: { id: string; name: string }, created: boolean, scanNow: boolean) => void;
	}

	let { open, library, onclose, onsaved }: Props = $props();

	let name = $state('');
	let path = $state('');
	let importSubfolders = $state(true);
	let schedule = $state<Schedule | 'manual' | 'unknown'>('manual');
	let scanNow = $state(true);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let touched = $state(false);

	$effect(() => {
		if (!open) return;
		name = library?.name ?? '';
		path = library?.path ?? '';
		importSubfolders = library?.importSubfolders ?? true;
		schedule = scheduleOf(library?.cronSchedule);
		scanNow = true;
		error = null;
		touched = false;
		saving = false;
	});

	const scheduleLabels: Record<Schedule | 'manual', () => string> = {
		manual: m.admin_libs_schedule_manual,
		hourly: m.admin_libs_schedule_hourly,
		daily: m.admin_libs_schedule_daily,
		weekly: m.admin_libs_schedule_weekly,
		monthly: m.admin_libs_schedule_monthly
	};

	const nameMissing = $derived(touched && !name.trim());
	const pathInvalid = $derived(touched && !/^(\/|[a-zA-Z]:[\\/]|\\\\)/.test(path.trim()));

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		touched = true;
		if (nameMissing || pathInvalid) return;
		saving = true;
		error = null;
		const body = {
			name: name.trim(),
			path: path.trim(),
			importSubfolders,
			// An expression the form doesn't know is kept as it was.
			cronSchedule: schedule === 'unknown' ? (library?.cronSchedule ?? null) : cronOf(schedule)
		};
		if (library) {
			const { error: failure, response } = await updateExternalLibrary({
				path: { id: library.id },
				body
			});
			saving = false;
			if (response?.ok) onsaved({ id: library.id, name: body.name }, false, false);
			else error = errorText(failure);
		} else {
			const { data, error: failure } = await createExternalLibrary({ body });
			saving = false;
			if (data) onsaved({ id: data.id, name: data.name }, true, scanNow);
			else error = errorText(failure);
		}
	}
</script>

<Dialog
	{open}
	title={library ? m.admin_libs_edit_title() : m.admin_libs_create_title()}
	onclose={() => !saving && onclose()}
	width="520px"
>
	<form id="library-form" class="form" novalidate onsubmit={submit}>
		<label class="field">
			<span>{m.admin_libs_name()}</span>
			<input
				bind:value={name}
				required
				autocomplete="off"
				aria-invalid={nameMissing ? 'true' : undefined}
			/>
			{#if nameMissing}<p class="field-error">{m.admin_libs_name_missing()}</p>{/if}
		</label>
		<label class="field">
			<span>{m.admin_libs_path()}</span>
			<input
				class="mono"
				bind:value={path}
				required
				autocomplete="off"
				spellcheck="false"
				placeholder="/mnt/fotos"
				aria-invalid={pathInvalid ? 'true' : undefined}
				aria-describedby="library-path-hint"
			/>
			{#if pathInvalid}
				<p class="field-error" id="library-path-hint">{m.admin_libs_path_invalid()}</p>
			{:else}
				<p class="hint" id="library-path-hint">{m.admin_libs_path_hint()}</p>
			{/if}
		</label>
		<label class="check">
			<input type="checkbox" bind:checked={importSubfolders} />
			{m.admin_libs_subfolders()}
		</label>
		<label class="field">
			<span>{m.admin_libs_schedule()}</span>
			<select bind:value={schedule}>
				<option value="manual">{scheduleLabels.manual()}</option>
				{#each SCHEDULES as value (value)}
					<option {value}>{scheduleLabels[value]()}</option>
				{/each}
				{#if schedule === 'unknown'}
					<option value="unknown"
						>{m.admin_libs_schedule_custom({ cron: library?.cronSchedule ?? '' })}</option
					>
				{/if}
			</select>
			<p class="hint">{m.admin_libs_schedule_hint()}</p>
		</label>
		{#if !library}
			<label class="check">
				<input type="checkbox" bind:checked={scanNow} />
				{m.admin_libs_scan_after_create()}
			</label>
		{/if}
		{#if error}<p class="field-error" role="alert">{error}</p>{/if}
	</form>
	{#snippet actions()}
		<button type="button" onclick={onclose} disabled={saving}>{m.admin_core_cancel()}</button>
		<button type="submit" form="library-form" class="primary" disabled={saving}>
			{library ? m.admin_core_save() : m.admin_libs_create()}
		</button>
	{/snippet}
</Dialog>

<style>
	.mono {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: var(--font-size-sm);
	}
</style>
