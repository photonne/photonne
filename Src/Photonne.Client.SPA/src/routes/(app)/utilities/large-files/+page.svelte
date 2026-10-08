<script lang="ts">
	import '#lib/search/ui.css';
	import { tick } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { deleteAssets, restoreAssets } from '#lib/api/index.js';
	import {
		getLargeFilesOptions,
		getLargeFilesQueryKey,
		getUtilitiesSummaryQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime, formatBytes } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { directoryOf } from '#lib/search/text.js';
	import UtilityHeader from '#lib/search/utilities/UtilityHeader.svelte';
	import ViewerHost from '#lib/search/utilities/ViewerHost.svelte';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	const COUNTS = [25, 50, 100, 200];
	const COUNT_KEY = 'photonne.utilities.largeFilesCount';

	const queryClient = useQueryClient();
	const viewer = new ViewerRoute();

	let count = $state(readCount());
	const files = createQuery(() => getLargeFilesOptions({ query: { count } }));
	const selected = new SvelteSet<string>();
	let busy = $state(false);

	function readCount() {
		try {
			const saved = Number(localStorage.getItem(COUNT_KEY));
			return COUNTS.includes(saved) ? saved : 50;
		} catch {
			return 50;
		}
	}

	const items = $derived(files.data ?? []);
	const order = $derived(items.map((item) => item.id));
	const total = $derived(items.reduce((sum, item) => sum + item.fileSize, 0));
	const selectedBytes = $derived(
		items.filter((item) => selected.has(item.id)).reduce((sum, item) => sum + item.fileSize, 0)
	);
	const allChecked = $derived(items.length > 0 && items.every((item) => selected.has(item.id)));

	function refresh() {
		queryClient.invalidateQueries({ queryKey: getLargeFilesQueryKey({ query: { count } }) });
		queryClient.invalidateQueries({ queryKey: getUtilitiesSummaryQueryKey() });
	}

	async function trash(ids: string[]) {
		if (busy || ids.length === 0) return;
		busy = true;
		try {
			const { error } = await deleteAssets({ body: { assetIds: ids } });
			if (error) throw error;
			for (const id of ids) selected.delete(id);
			refresh();
			toasts.show(m.utilities_trashed({ count: ids.length }), {
				action: {
					label: m.utilities_undo(),
					run: async () => {
						const { error } = await restoreAssets({ body: { assetIds: ids } });
						if (error) toasts.error(m.utilities_failed());
						refresh();
					}
				}
			});
		} catch {
			toasts.error(m.utilities_failed());
		} finally {
			busy = false;
		}
	}

	async function focusRow(id: string) {
		await tick();
		document.querySelector<HTMLElement>(`[data-open="${CSS.escape(id)}"]`)?.focus();
	}
</script>

<div class="page">
	<UtilityHeader title={m.utilities_large_files()} lead={m.utilities_large_files_lead({ count })}>
		{#snippet toolbar()}
			<label class="count">
				{m.utilities_show()}
				<select
					class="ui-field"
					value={count}
					onchange={(event) => {
						count = Number(event.currentTarget.value);
						selected.clear();
						try {
							localStorage.setItem(COUNT_KEY, String(count));
						} catch {
							// Private mode: the choice just isn't remembered.
						}
					}}
				>
					{#each COUNTS as option (option)}
						<option value={option}>{option}</option>
					{/each}
				</select>
			</label>
			<button
				type="button"
				class="ui-button danger"
				disabled={selected.size === 0 || busy}
				onclick={() => trash([...selected])}
			>
				<Icon name="delete" size={18} />
				{selected.size ? m.utilities_trash_selected({ count: selected.size }) : m.utilities_trash()}
			</button>
		{/snippet}
	</UtilityHeader>

	{#if files.isPending}
		<p class="ui-status" role="status">{m.utilities_loading()}</p>
	{:else if files.isError}
		<p class="ui-status" role="alert">{m.error_loading()}</p>
	{:else if items.length === 0}
		<p class="ui-status">{m.utilities_large_files_empty()}</p>
	{:else}
		<p class="summary" aria-live="polite">
			{m.utilities_large_files_total({ size: formatBytes(total) })}
			{#if selected.size > 0}
				· <strong
					>{m.utilities_marked_summary({
						count: selected.size,
						size: formatBytes(selectedBytes)
					})}</strong
				>
			{/if}
		</p>
		<div class="table-wrap">
			<table>
				<thead>
					<tr>
						<th scope="col" class="check">
							<input
								type="checkbox"
								aria-label={m.utilities_select_all()}
								checked={allChecked}
								indeterminate={selected.size > 0 && !allChecked}
								onchange={() => {
									if (allChecked) selected.clear();
									else for (const item of items) selected.add(item.id);
								}}
							/>
						</th>
						<th scope="col" class="rank">#</th>
						<th scope="col">{m.utilities_col_file()}</th>
						<th scope="col" class="type">{m.utilities_col_type()}</th>
						<th scope="col" class="num">{m.utilities_col_size()}</th>
						<th scope="col" class="date">{m.utilities_col_date()}</th>
					</tr>
				</thead>
				<tbody>
					{#each items as item, index (item.id)}
						<tr class:selected={selected.has(item.id)}>
							<td class="check">
								<input
									type="checkbox"
									aria-label={m.utilities_select_file({ name: item.fileName })}
									checked={selected.has(item.id)}
									onchange={() =>
										selected.has(item.id) ? selected.delete(item.id) : selected.add(item.id)}
								/>
							</td>
							<td class="rank">{index + 1}</td>
							<td>
								<button
									type="button"
									class="file"
									data-open={item.id}
									onclick={() => viewer.open(item.id)}
								>
									<img
										src={thumbnailUrl(item.id, 'Small', item.thumbnailsGeneratedAt)}
										alt=""
										loading="lazy"
									/>
									<span class="names">
										<span class="name">{item.fileName}</span>
										<span class="path">{directoryOf(item.fullPath)}</span>
									</span>
								</button>
							</td>
							<td class="type">
								{item.type === 'Video' ? m.utilities_type_video() : m.utilities_type_photo()}
							</td>
							<td class="num">{formatBytes(item.fileSize)}</td>
							<td class="date">{dateTime(item.fileCreatedAt)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
	{/if}
</div>

<ViewerHost
	{viewer}
	{order}
	versionOf={(id) => items.find((item) => item.id === id)?.thumbnailsGeneratedAt}
	ontrash={(id) => trash([id])}
	onclosed={focusRow}
/>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.count {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.summary {
		margin: 0;
		padding: 0 var(--space-4) var(--space-3);
		color: var(--color-text-muted);
	}

	.summary strong {
		color: var(--color-danger);
	}

	.table-wrap {
		padding: 0 var(--space-4);
		overflow-x: auto;
	}

	table {
		width: 100%;
		border-collapse: collapse;
		font-size: var(--font-size-sm);
	}

	th {
		position: sticky;
		top: 0;
		z-index: 1;
		padding: var(--space-2);
		border-bottom: 1px solid var(--color-border);
		background: var(--color-bg);
		color: var(--color-text-muted);
		font-weight: 600;
		text-align: left;
	}

	td {
		padding: var(--space-1) var(--space-2);
		border-bottom: 1px solid var(--color-border);
	}

	tr.selected td {
		background: color-mix(in srgb, var(--color-accent) 10%, transparent);
	}

	.check {
		width: 36px;
	}

	.rank {
		width: 40px;
		color: var(--color-text-muted);
		font-variant-numeric: tabular-nums;
	}

	.num,
	.date {
		white-space: nowrap;
		font-variant-numeric: tabular-nums;
	}

	.num {
		text-align: right;
		font-weight: 600;
	}

	th.num {
		text-align: right;
	}

	.file {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		width: 100%;
		padding: var(--space-1);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		text-align: left;
		cursor: zoom-in;
	}

	.file:hover {
		background: var(--color-surface);
	}

	.file img {
		flex: none;
		width: 56px;
		height: 42px;
		border-radius: 4px;
		object-fit: cover;
		background: var(--color-placeholder);
	}

	.names {
		display: grid;
		min-width: 0;
	}

	.name,
	.path {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.name {
		font-weight: 600;
	}

	.path {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	@media (max-width: 1000px) {
		.type,
		.date {
			display: none;
		}
	}
</style>
