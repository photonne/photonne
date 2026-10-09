<script lang="ts">
	import { useQueryClient } from '@tanstack/svelte-query';
	import {
		deleteUnsupportedFile,
		getUnsupportedFileContent,
		getUnsupportedFiles,
		type UnsupportedFileResponse
	} from '#lib/api/index.js';
	import { getUtilitiesSummaryQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime, formatBytes } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import { filePath } from '#lib/search/icons.js';
	import { directoryOf } from '#lib/search/text.js';
	import UtilityHeader from '#lib/search/utilities/UtilityHeader.svelte';

	const PAGE_SIZE = 100;
	const queryClient = useQueryClient();

	let files = $state<UnsupportedFileResponse[]>([]);
	let status = $state<'pending' | 'error' | 'ready'>('pending');
	let cursor = $state<string | null>(null);
	let hasMore = $state(false);
	let loadingMore = $state(false);
	let deleting = $state<UnsupportedFileResponse | null>(null);
	let busy = $state(false);

	async function load(more = false) {
		if (more) loadingMore = true;
		const { data } = await getUnsupportedFiles({
			query: { pageSize: PAGE_SIZE, cursor: more ? (cursor ?? undefined) : undefined }
		});
		loadingMore = false;
		if (!data) {
			if (!more) status = 'error';
			else toasts.error(m.error_loading());
			return;
		}
		files = more ? [...files, ...data.items] : data.items;
		cursor = data.nextCursor ?? null;
		hasMore = data.hasMore;
		status = 'ready';
	}
	load();

	async function download(file: UnsupportedFileResponse) {
		const { data } = await getUnsupportedFileContent({ path: { id: file.id }, parseAs: 'blob' });
		if (!(data instanceof Blob)) {
			toasts.error(m.utilities_failed());
			return;
		}
		const url = URL.createObjectURL(data);
		Object.assign(document.createElement('a'), { href: url, download: file.fileName }).click();
		setTimeout(() => URL.revokeObjectURL(url), 60_000);
	}

	async function remove(file: UnsupportedFileResponse) {
		deleting = null;
		busy = true;
		try {
			const { error } = await deleteUnsupportedFile({ path: { id: file.id } });
			if (error) throw error;
			files = files.filter((f) => f.id !== file.id);
			queryClient.invalidateQueries({ queryKey: getUtilitiesSummaryQueryKey() });
			toasts.show(m.utilities_unsupported_deleted({ name: file.fileName }));
		} catch {
			toasts.error(m.utilities_failed());
		} finally {
			busy = false;
		}
	}
</script>

<div class="page">
	<UtilityHeader title={m.utilities_unsupported()} lead={m.utilities_unsupported_lead()} />

	{#if status === 'pending'}
		<Skeleton variant="rows" count={8} />
	{:else if status === 'error'}
		<div role="alert"><EmptyState icon="info" title={m.error_loading()} /></div>
	{:else if files.length === 0}
		<EmptyState iconPath={filePath} title={m.utilities_unsupported_empty()} />
	{:else}
		<div class="table-wrap">
			<table>
				<thead>
					<tr>
						<th scope="col">{m.utilities_col_file()}</th>
						<th scope="col" class="ext">{m.utilities_col_extension()}</th>
						<th scope="col" class="num">{m.utilities_col_size()}</th>
						<th scope="col" class="date">{m.utilities_col_found()}</th>
						<th scope="col" class="actions"
							><span class="visually-hidden">{m.utilities_col_actions()}</span></th
						>
					</tr>
				</thead>
				<tbody>
					{#each files as file (file.id)}
						<tr>
							<td>
								<span class="names">
									<span class="name">{file.fileName}</span>
									<span class="path" title={file.fullPath}>{directoryOf(file.fullPath)}</span>
								</span>
							</td>
							<td class="ext"><span class="tag">{file.extension.replace(/^\./, '')}</span></td>
							<td class="num">{formatBytes(file.fileSize)}</td>
							<td class="date">{dateTime(file.discoveredAt)}</td>
							<td class="actions">
								<button
									type="button"
									class="icon-btn"
									title={m.utilities_download()}
									aria-label={m.utilities_download_file({ name: file.fileName })}
									onclick={() => download(file)}
								>
									<Icon name="download" size={20} />
								</button>
								{#if file.canDelete}
									<button
										type="button"
										class="icon-btn"
										title={m.utilities_delete()}
										aria-label={m.utilities_delete_file({ name: file.fileName })}
										disabled={busy}
										onclick={() => (deleting = file)}
									>
										<Icon name="delete" size={20} />
									</button>
								{/if}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
		{#if hasMore}
			<div class="more">
				<button type="button" class="btn" disabled={loadingMore} onclick={() => load(true)}>
					{loadingMore ? m.utilities_loading() : m.utilities_load_more()}
				</button>
			</div>
		{/if}
	{/if}
</div>

<Dialog
	open={deleting !== null}
	title={m.utilities_delete_title()}
	onclose={() => (deleting = null)}
>
	<p>{m.utilities_delete_body({ name: deleting?.fileName ?? '' })}</p>
	{#snippet actions()}
		<button type="button" onclick={() => (deleting = null)}>{m.utilities_cancel()}</button>
		<button type="button" class="danger" onclick={() => deleting && remove(deleting)}>
			{m.utilities_delete_forever()}
		</button>
	{/snippet}
</Dialog>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.table-wrap {
		padding: 0 var(--page-gutter);
		overflow-x: auto;
	}

	table {
		width: 100%;
		border-collapse: collapse;
		font-size: var(--font-size-sm);
	}

	th {
		padding: var(--space-2);
		border-bottom: 1px solid var(--color-border);
		color: var(--color-text-muted);
		font-weight: 600;
		text-align: left;
	}

	td {
		padding: var(--space-2);
		border-bottom: 1px solid var(--color-border);
	}

	tr:hover td {
		background: var(--color-hover);
	}

	.names {
		display: grid;
		min-width: 0;
	}

	.name {
		font-weight: 600;
		word-break: break-all;
	}

	.path {
		overflow: hidden;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		text-overflow: ellipsis;
		white-space: nowrap;
		max-width: 520px;
	}

	.tag {
		padding: 1px var(--space-2);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-family: ui-monospace, monospace;
		font-size: var(--font-size-xs);
		text-transform: uppercase;
	}

	.num,
	.date {
		white-space: nowrap;
		font-variant-numeric: tabular-nums;
	}

	.num {
		text-align: right;
	}

	.actions {
		width: 88px;
		white-space: nowrap;
		text-align: right;
	}

	.more {
		display: flex;
		justify-content: center;
		padding: var(--space-4);
	}

	@media (max-width: 1000px) {
		.date {
			display: none;
		}
	}
</style>
