<script lang="ts">
	import { onDestroy } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { deleteExternalLibrary, type ExternalLibraryDto } from '#lib/api/index.js';
	import {
		getExternalLibrariesOptions,
		getExternalLibrariesQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AdminPage from '#lib/admin/AdminPage.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { errorText } from '#lib/admin/errors.js';
	import { count, localDateTime, percent, relativeTime } from '#lib/admin/format.js';
	import { adminIcons } from '#lib/admin/icons.js';
	import { scheduleOf, type ScanState } from '#lib/admin/libraries.js';
	import LibraryDialog from '#lib/admin/LibraryDialog.svelte';
	import LibraryPermissionsDialog from '#lib/admin/LibraryPermissionsDialog.svelte';
	import { LibraryScanner } from '#lib/admin/library-scan.svelte.js';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';

	const queryClient = useQueryClient();
	const libraries = createQuery(() => getExternalLibrariesOptions());

	let editing = $state<{ library: ExternalLibraryDto | null } | null>(null);
	let sharing = $state<ExternalLibraryDto | null>(null);
	let deleting = $state<ExternalLibraryDto | null>(null);
	let busy = $state(false);

	const scanner = new LibraryScanner(finished);
	scanner.resume();
	onDestroy(() => scanner.stop());

	function refresh() {
		return queryClient.invalidateQueries({ queryKey: getExternalLibrariesQueryKey() });
	}

	function nameOf(id: string) {
		return libraries.data?.find((l) => l.id === id)?.name ?? '';
	}

	function finished(scan: ScanState) {
		const name = nameOf(scan.libraryId);
		if (scan.cancelled) toasts.show(m.admin_libs_scan_cancelled({ name }));
		else if (scan.error === 'connection') toasts.error(m.admin_libs_scan_lost({ name }));
		else if (scan.error) toasts.error(m.admin_libs_scan_failed({ name, error: scan.error }));
		else toasts.show(m.admin_libs_scan_done({ name, count: scan.indexed, n: count(scan.indexed) }));
		refresh();
	}

	async function saved(library: { id: string; name: string }, created: boolean, scanNow: boolean) {
		editing = null;
		toasts.show(
			created
				? m.admin_libs_created({ name: library.name })
				: m.admin_libs_updated({ name: library.name })
		);
		await refresh();
		if (scanNow) scanner.start(library.id);
	}

	async function remove() {
		if (!deleting) return;
		const library = deleting;
		busy = true;
		const { error } = await deleteExternalLibrary({ path: { id: library.id } });
		busy = false;
		deleting = null;
		if (error) {
			toasts.error(errorText(error));
			return;
		}
		toasts.show(m.admin_libs_deleted({ name: library.name }));
		refresh();
	}

	const scheduleLabels = {
		manual: m.admin_libs_schedule_manual,
		hourly: m.admin_libs_schedule_hourly,
		daily: m.admin_libs_schedule_daily,
		weekly: m.admin_libs_schedule_weekly,
		monthly: m.admin_libs_schedule_monthly
	};

	function scheduleText(cron: string | null) {
		const schedule = scheduleOf(cron);
		return schedule === 'unknown' ? (cron ?? '') : scheduleLabels[schedule]();
	}

	const statusIcon: Record<string, string> = {
		Completed: adminIcons.checkCircle,
		Failed: adminIcons.error,
		Running: adminIcons.sync
	};
</script>

<AdminPage title={m.admin_libraries()} description={m.admin_libs_description()}>
	{#snippet actions()}
		<button type="button" class="btn primary" onclick={() => (editing = { library: null })}>
			<Icon name="add" size={18} />
			{m.admin_libs_new()}
		</button>
	{/snippet}

	{#if libraries.isPending}
		<div class="loading"><Skeleton variant="rows" count={3} /></div>
	{:else if libraries.isError}
		<p class="notice danger" role="alert">{m.error_loading()}</p>
	{:else if libraries.data.length === 0}
		<div class="card">
			<EmptyState
				iconPath={adminIcons.folderSpecial}
				title={m.admin_libs_empty_title()}
				hint={m.admin_libs_empty_body()}
			>
				{#snippet action()}
					<button type="button" class="btn primary" onclick={() => (editing = { library: null })}>
						<Icon name="add" size={18} />
						{m.admin_libs_new()}
					</button>
				{/snippet}
			</EmptyState>
		</div>
	{:else}
		<ul class="libraries">
			{#each libraries.data as library (library.id)}
				{@const scan = scanner.scan?.libraryId === library.id ? scanner.scan : null}
				<li>
					<article class="card library" aria-labelledby="lib-{library.id}">
						<div class="head">
							<span class="lib-icon"><Icon path={adminIcons.folderSpecial} /></span>
							<div class="title">
								<h2 id="lib-{library.id}">{library.name}</h2>
								<code title={library.path}>{library.path}</code>
							</div>
							<div class="tools">
								<button
									type="button"
									class="icon-btn sm"
									aria-label={m.admin_libs_access_named({ name: library.name })}
									title={m.admin_libs_access()}
									onclick={() => (sharing = library)}
								>
									<Icon name="people" size={18} />
								</button>
								<button
									type="button"
									class="icon-btn sm"
									aria-label={m.admin_libs_edit_named({ name: library.name })}
									title={m.admin_core_edit()}
									disabled={!!scan}
									onclick={() => (editing = { library })}
								>
									<Icon name="edit" size={18} />
								</button>
								<button
									type="button"
									class="icon-btn sm"
									aria-label={m.admin_libs_delete_named({ name: library.name })}
									title={m.admin_core_delete()}
									disabled={!!scan}
									onclick={() => (deleting = library)}
								>
									<Icon name="delete" size={18} />
								</button>
							</div>
						</div>

						<ul class="facts">
							<li>
								<Icon name="photos" size={16} />{m.admin_libs_assets({
									count: library.assetCount,
									n: count(library.assetCount)
								})}
							</li>
							<li>
								<Icon path={adminIcons.schedule} size={16} />{scheduleText(library.cronSchedule)}
							</li>
							{#if library.importSubfolders}
								<li><Icon name="folder" size={16} />{m.admin_libs_with_subfolders()}</li>
							{/if}
						</ul>

						{#if scan}
							<div class="scan" role="status" aria-live="polite">
								<div class="scan-head">
									<strong
										>{scan.resumed ? m.admin_libs_scan_resumed() : m.admin_libs_scanning()}</strong
									>
									<span class="num">{percent(scan.percentage / 100)}</span>
								</div>
								<div
									class="bar"
									role="progressbar"
									aria-valuemin="0"
									aria-valuemax="100"
									aria-valuenow={scan.percentage}
									aria-label={m.admin_libs_scan_progress({ name: library.name })}
								>
									<span style:width="{scan.percentage}%"></span>
								</div>
								{#if scan.message}<p class="muted small">{scan.message}</p>{/if}
								<p class="small counts">
									<span>{m.admin_libs_scan_found({ count: scan.found, n: count(scan.found) })}</span
									>
									<span
										>{m.admin_libs_scan_indexed({
											count: scan.indexed,
											n: count(scan.indexed)
										})}</span
									>
									{#if scan.missing}
										<span class="warn"
											>{m.admin_libs_scan_missing({
												count: scan.missing,
												n: count(scan.missing)
											})}</span
										>
									{/if}
								</p>
							</div>
						{:else}
							<p class="last small">
								{#if library.lastScannedAt}
									<span class="status status-{library.lastScanStatus.toLowerCase()}">
										<Icon
											path={statusIcon[library.lastScanStatus] ?? adminIcons.schedule}
											size={16}
										/>
										{library.lastScanStatus === 'Failed'
											? m.admin_libs_last_failed()
											: m.admin_libs_last_scan()}
										<time
											datetime={library.lastScannedAt}
											title={localDateTime(library.lastScannedAt)}
											>{relativeTime(library.lastScannedAt)}</time
										>
									</span>
									{#if library.lastScanAssetsFound !== null}
										<span class="muted">
											{m.admin_libs_last_counts({
												found: count(library.lastScanAssetsFound),
												added: count(library.lastScanAssetsAdded ?? 0),
												removed: count(library.lastScanAssetsRemoved ?? 0)
											})}
										</span>
									{/if}
								{:else}
									<span class="muted">{m.admin_libs_never_scanned()}</span>
								{/if}
							</p>
						{/if}

						<div class="foot">
							{#if scan}
								<button
									type="button"
									class="btn"
									disabled={scan.cancelled}
									onclick={() => scanner.cancel()}
								>
									<Icon path={adminIcons.stop} size={18} />
									{m.admin_libs_scan_cancel()}
								</button>
							{:else}
								<button
									type="button"
									class="btn"
									disabled={scanner.running}
									title={scanner.running ? m.admin_libs_scan_busy() : undefined}
									onclick={() => scanner.start(library.id)}
								>
									<Icon path={adminIcons.sync} size={18} />
									{m.admin_libs_scan()}
								</button>
							{/if}
						</div>
					</article>
				</li>
			{/each}
		</ul>
	{/if}

	<LibraryDialog
		open={editing !== null}
		library={editing?.library ?? null}
		onclose={() => (editing = null)}
		onsaved={saved}
	/>

	<LibraryPermissionsDialog library={sharing} onclose={() => (sharing = null)} />

	<ConfirmDialog
		danger
		open={deleting !== null}
		title={m.admin_libs_delete_title()}
		confirmLabel={m.admin_core_delete()}
		{busy}
		onconfirm={remove}
		onclose={() => (deleting = null)}
	>
		<p>{m.admin_libs_delete_body({ name: deleting?.name ?? '' })}</p>
		<p class="muted small">{m.admin_libs_delete_note()}</p>
	</ConfirmDialog>
</AdminPage>

<style>
	h2 {
		margin: 0;
		font-size: var(--font-size-md);
	}

	p {
		margin: 0;
	}

	.loading {
		margin: 0 calc(-1 * var(--page-gutter));
	}

	.libraries {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
		gap: var(--space-3);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.library {
		height: 100%;
		display: grid;
		gap: var(--space-3);
		align-content: start;
	}

	.head {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
	}

	.lib-icon {
		flex: none;
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border-radius: var(--radius-control);
		background: var(--color-brand-tile);
		color: var(--color-accent);
	}

	.title {
		flex: 1;
		display: grid;
		min-width: 0;
	}

	.title code {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	.tools {
		display: flex;
		gap: 2px;
	}

	.facts {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1) var(--space-4);
		margin: 0;
		padding: 0;
		list-style: none;
		font-size: var(--font-size-sm);
	}

	.facts li {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		color: var(--color-text-muted);
	}

	.last {
		display: grid;
		gap: 2px;
	}

	.status {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
	}

	.status-completed :global(svg) {
		color: var(--color-success);
	}

	.status-failed {
		color: var(--color-danger);
	}

	.scan {
		display: grid;
		gap: var(--space-1);
		padding: var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
	}

	.scan-head {
		display: flex;
		justify-content: space-between;
		font-size: var(--font-size-sm);
	}

	.counts {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	.warn {
		color: var(--color-warning);
	}

	.foot {
		display: flex;
		justify-content: flex-end;
	}
</style>
