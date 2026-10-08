<script lang="ts">
	import {
		exportDatabaseBackup,
		restoreDatabaseBackup,
		type RestoreBackupResponse
	} from '#lib/api/index.js';
	import AdminPage from '#lib/admin/AdminPage.svelte';
	import {
		BACKUP_LEVELS,
		fileNameFromDisposition,
		summarizeBackup,
		type BackupLevel,
		type BackupSummary
	} from '#lib/admin/backup.js';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { errorText } from '#lib/admin/errors.js';
	import { count, localDateTime } from '#lib/admin/format.js';
	import { adminIcons } from '#lib/admin/icons.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { fileStamp, formatBytes } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';

	let level = $state<BackupLevel>('essential');
	let exporting = $state(false);

	let file = $state<File | null>(null);
	let summary = $state<BackupSummary | null>(null);
	let reading = $state(false);
	let unreadable = $state(false);
	let dragging = $state(false);
	let confirming = $state(false);
	let understood = $state(false);
	let restoring = $state(false);
	let result = $state<RestoreBackupResponse | null>(null);
	let input = $state<HTMLInputElement>();

	const levelText: Record<BackupLevel, { title: () => string; text: () => string }> = {
		config: { title: m.admin_backup_level_config, text: m.admin_backup_level_config_text },
		essential: { title: m.admin_backup_level_essential, text: m.admin_backup_level_essential_text },
		full: { title: m.admin_backup_level_full, text: m.admin_backup_level_full_text }
	};

	async function download() {
		exporting = true;
		const { data, error, response } = await exportDatabaseBackup({
			query: { level },
			parseAs: 'blob'
		});
		exporting = false;
		if (error || !(data instanceof Blob)) {
			toasts.error(m.admin_backup_export_failed());
			return;
		}
		const name =
			fileNameFromDisposition(response?.headers.get('content-disposition') ?? null) ??
			`photonne_backup_${level}_${fileStamp()}.json`;
		const url = URL.createObjectURL(data);
		Object.assign(document.createElement('a'), { href: url, download: name }).click();
		setTimeout(() => URL.revokeObjectURL(url), 60_000);
		toasts.show(m.admin_backup_exported({ size: formatBytes(data.size) }));
	}

	async function pick(picked: File | undefined | null) {
		result = null;
		summary = null;
		unreadable = false;
		file = picked ?? null;
		if (!picked) return;
		if (!picked.name.toLowerCase().endsWith('.json')) {
			unreadable = true;
			return;
		}
		reading = true;
		summary = summarizeBackup(await picked.text());
		reading = false;
		unreadable = summary === null;
	}

	function clear() {
		file = null;
		summary = null;
		unreadable = false;
		if (input) input.value = '';
	}

	function ondrop(event: DragEvent) {
		event.preventDefault();
		dragging = false;
		pick(event.dataTransfer?.files[0]);
	}

	async function restore() {
		if (!file) return;
		restoring = true;
		const { data, error } = await restoreDatabaseBackup({ body: { file } });
		restoring = false;
		confirming = false;
		if (!data) {
			toasts.error(errorText(error, m.admin_backup_restore_failed));
			return;
		}
		result = data;
		clear();
		toasts.show(m.admin_backup_restored());
	}
</script>

<AdminPage title={m.admin_backup()} description={m.admin_backup_description()} narrow>
	<p class="notice">
		<Icon name="info" />
		<span>{m.admin_backup_media_note()}</span>
	</p>

	<div class="columns">
		<section class="card" aria-labelledby="export-title">
			<h2 id="export-title">
				<Icon path={adminIcons.cloudDownload} />
				{m.admin_backup_export_title()}
			</h2>
			<fieldset>
				<legend class="visually-hidden">{m.admin_backup_level()}</legend>
				{#each BACKUP_LEVELS as value (value)}
					<label class="level" class:chosen={level === value}>
						<input type="radio" name="level" {value} bind:group={level} />
						<span>
							<strong>{levelText[value].title()}</strong>
							<span class="muted small">{levelText[value].text()}</span>
						</span>
					</label>
				{/each}
			</fieldset>
			<div class="foot">
				<button type="button" class="btn primary" onclick={download} disabled={exporting}>
					<Icon name="download" size={18} />
					{exporting ? m.admin_backup_exporting() : m.admin_backup_export()}
				</button>
			</div>
		</section>

		<section class="card" aria-labelledby="restore-title">
			<h2 id="restore-title">
				<Icon path={adminIcons.cloudUpload} />
				{m.admin_backup_restore_title()}
			</h2>
			<p class="notice danger">
				<Icon path={adminIcons.warning} />
				<span>{m.admin_backup_restore_warning()}</span>
			</p>

			{#if !file}
				<div
					class="drop"
					class:dragging
					role="group"
					aria-label={m.admin_backup_drop_label()}
					ondragover={(event) => {
						event.preventDefault();
						dragging = true;
					}}
					ondragleave={() => (dragging = false)}
					{ondrop}
				>
					<Icon path={adminIcons.attachFile} size={28} />
					<span>{m.admin_backup_drop()}</span>
					<button type="button" class="btn" onclick={() => input?.click()}>
						{m.admin_backup_choose()}
					</button>
				</div>
			{:else}
				<div class="file">
					<div class="file-head">
						<Icon path={adminIcons.attachFile} size={18} />
						<strong class="file-name">{file.name}</strong>
						<span class="muted small">{formatBytes(file.size)}</span>
						<button
							type="button"
							class="icon-btn"
							aria-label={m.admin_backup_clear()}
							title={m.admin_backup_clear()}
							onclick={clear}
						>
							<Icon name="close" size={18} />
						</button>
					</div>
					{#if reading}
						<p class="muted small" role="status">{m.admin_backup_reading()}</p>
					{:else if unreadable}
						<p class="field-error" role="alert">{m.admin_core_error_invalid_backup()}</p>
					{:else if summary}
						<p class="small">
							<span class="badge accent">{levelText[summary.level].title()}</span>
							{#if summary.createdAt}
								<span class="muted">
									{m.admin_backup_created_at({ date: localDateTime(summary.createdAt) })}</span
								>
							{/if}
							<span class="muted">· v{summary.version}</span>
						</p>
						{@render counts(summary, summary.level !== 'config', summary.level === 'full')}
						{#if summary.level === 'config'}
							<p class="notice warning small">{m.admin_backup_config_warning()}</p>
						{:else if summary.level === 'essential'}
							<p class="notice small">{m.admin_backup_essential_note()}</p>
						{/if}
					{/if}
				</div>
			{/if}
			<input
				bind:this={input}
				class="visually-hidden"
				type="file"
				accept=".json,application/json"
				tabindex="-1"
				aria-hidden="true"
				onchange={(event) => pick(event.currentTarget.files?.[0])}
			/>

			<div class="foot">
				<button
					type="button"
					class="btn danger"
					disabled={!summary || restoring}
					onclick={() => {
						understood = false;
						confirming = true;
					}}
				>
					{restoring ? m.admin_backup_restoring() : m.admin_backup_restore()}
				</button>
			</div>
		</section>
	</div>

	{#if result}
		<section class="card notice success result" aria-labelledby="result-title" role="status">
			<Icon path={adminIcons.checkCircle} />
			<div>
				<h2 id="result-title">{m.admin_backup_restored()}</h2>
				{@render counts(result.stats, result.stats.includesLibrary, result.stats.includesMlData)}
				{#if !result.stats.includesLibrary}
					<p class="small">{m.admin_backup_result_config()}</p>
				{:else if !result.stats.includesMlData}
					<p class="small">{m.admin_backup_result_essential()}</p>
				{/if}
				<p class="small muted">{m.admin_backup_result_session()}</p>
			</div>
		</section>
	{/if}

	<ConfirmDialog
		danger
		open={confirming}
		title={m.admin_backup_confirm_title()}
		confirmLabel={m.admin_backup_confirm()}
		busy={restoring}
		confirmDisabled={!understood}
		onconfirm={restore}
		onclose={() => (confirming = false)}
	>
		<p>{m.admin_backup_confirm_body({ name: file?.name ?? '' })}</p>
		{#if summary}{@render counts(
				summary,
				summary.level !== 'config',
				summary.level === 'full'
			)}{/if}
		<label class="check">
			<input type="checkbox" bind:checked={understood} disabled={restoring} />
			{m.admin_backup_confirm_check()}
		</label>
	</ConfirmDialog>
</AdminPage>

{#snippet counts(
	data: {
		users: number;
		folders: number;
		externalLibraries: number;
		assets: number;
		albums: number;
		people: number;
		faces: number;
		embeddings: number;
		ocrLines: number;
	},
	library: boolean,
	ml: boolean
)}
	<dl class="counts">
		<div>
			<dt>{m.admin_backup_count_users()}</dt>
			<dd>{count(data.users)}</dd>
		</div>
		<div>
			<dt>{m.admin_backup_count_folders()}</dt>
			<dd>{count(data.folders)}</dd>
		</div>
		<div>
			<dt>{m.admin_backup_count_libraries()}</dt>
			<dd>{count(data.externalLibraries)}</dd>
		</div>
		{#if library}
			<div>
				<dt>{m.admin_backup_count_assets()}</dt>
				<dd>{count(data.assets)}</dd>
			</div>
			<div>
				<dt>{m.admin_backup_count_albums()}</dt>
				<dd>{count(data.albums)}</dd>
			</div>
		{/if}
		{#if ml}
			<div>
				<dt>{m.admin_backup_count_people()}</dt>
				<dd>{count(data.people)}</dd>
			</div>
			<div>
				<dt>{m.admin_backup_count_faces()}</dt>
				<dd>{count(data.faces)}</dd>
			</div>
			<div>
				<dt>{m.admin_backup_count_embeddings()}</dt>
				<dd>{count(data.embeddings)}</dd>
			</div>
			<div>
				<dt>{m.admin_backup_count_ocr()}</dt>
				<dd>{count(data.ocrLines)}</dd>
			</div>
		{/if}
	</dl>
{/snippet}

<style>
	h2 {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		margin: 0 0 var(--space-3);
		font-size: var(--font-size-md);
	}

	p {
		margin: 0;
	}

	.columns {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: var(--space-3);
		align-items: stretch;
	}

	.card {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.card > h2 {
		margin: 0;
	}

	fieldset {
		gap: var(--space-2);
	}

	.level {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		cursor: pointer;
	}

	.level.chosen {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}

	.level input {
		margin-top: 3px;
		accent-color: var(--color-accent);
	}

	.level > span {
		display: grid;
		gap: 2px;
	}

	.foot {
		margin-top: auto;
		display: flex;
		justify-content: flex-end;
	}

	.drop {
		flex: 1;
		display: grid;
		justify-items: center;
		align-content: center;
		gap: var(--space-2);
		min-height: 160px;
		padding: var(--space-4);
		border: 2px dashed var(--color-border);
		border-radius: var(--radius-md);
		color: var(--color-text-muted);
		text-align: center;
	}

	.drop.dragging {
		border-color: var(--color-accent);
		background: var(--color-surface);
		color: var(--color-accent);
	}

	.file {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
	}

	.file-head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.file-name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.counts {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
		gap: var(--space-2);
		margin: 0;
	}

	.counts dt {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	.counts dd {
		margin: 0;
		font-weight: 600;
		font-variant-numeric: tabular-nums;
	}

	.result {
		display: flex;
		gap: var(--space-3);
	}

	.result > div {
		display: grid;
		gap: var(--space-2);
		flex: 1;
	}

	.result h2 {
		margin: 0;
	}

	@media (max-width: 1000px) {
		.columns {
			grid-template-columns: minmax(0, 1fr);
		}
	}
</style>
