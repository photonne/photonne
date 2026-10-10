<script lang="ts">
	import { tick } from 'svelte';
	import { useQueryClient } from '@tanstack/svelte-query';
	import {
		deleteAssets,
		getMyDuplicates,
		restoreAssets,
		type TimelineResponse,
		type UserDuplicateGroupResponse
	} from '#lib/api/index.js';
	import { getUtilitiesSummaryQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime, formatBytes } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { duplicatesPath, magicPath } from '#lib/search/icons.js';
	import { directoryOf } from '#lib/search/text.js';
	import {
		keepInAll,
		keepOnly,
		largest,
		markedBytes,
		oldest,
		recoverableBytes,
		splitFolders,
		toggleCopy,
		withoutAssets
	} from '#lib/search/utilities/duplicates.js';
	import UtilityHeader from '#lib/search/utilities/UtilityHeader.svelte';
	import ViewerHost from '#lib/search/utilities/ViewerHost.svelte';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	const queryClient = useQueryClient();
	const viewer = new ViewerRoute();

	let groups = $state<UserDuplicateGroupResponse[] | null>(null);
	let failed = $state(false);
	let marked = $state<ReadonlySet<string>>(new Set());
	let confirming = $state(false);
	let busy = $state(false);

	async function load() {
		failed = false;
		const { data } = await getMyDuplicates();
		if (!data) {
			failed = true;
			return;
		}
		groups = data;
		// What was marked and still exists stays marked.
		const ids = new Set(data.flatMap((group) => group.assets.map((asset) => asset.id)));
		marked = new Set([...marked].filter((id) => ids.has(id)));
	}
	load();

	const all = $derived(groups ?? []);
	const order = $derived(all.flatMap((group) => group.assets.map((asset) => asset.id)));
	const byId = $derived(
		Object.fromEntries(all.flatMap((group) => group.assets.map((asset) => [asset.id, asset])))
	);
	const recoverable = $derived(recoverableBytes(all));
	const selectedBytes = $derived(markedBytes(all, marked));

	function trash(ids: string[]) {
		if (busy || ids.length === 0) return;
		busy = true;
		return (async () => {
			try {
				const { error } = await deleteAssets({ body: { assetIds: ids } });
				if (error) throw error;
				groups = withoutAssets(all, ids);
				const gone = new Set(ids);
				marked = new Set([...marked].filter((id) => !gone.has(id)));
				queryClient.invalidateQueries({ queryKey: getUtilitiesSummaryQueryKey() });
				toasts.show(m.utilities_trashed({ count: ids.length }), {
					action: {
						label: m.utilities_undo(),
						run: async () => {
							const { error } = await restoreAssets({ body: { assetIds: ids } });
							if (error) toasts.error(m.utilities_failed());
							queryClient.invalidateQueries({ queryKey: getUtilitiesSummaryQueryKey() });
							await load();
						}
					}
				});
			} catch {
				toasts.error(m.utilities_failed());
			} finally {
				busy = false;
			}
		})();
	}

	function copyLabel(asset: TimelineResponse) {
		return m.utilities_copy_label({ name: asset.fileName, folder: directoryOf(asset.fullPath) });
	}

	async function focusCopy(id: string) {
		await tick();
		document.querySelector<HTMLElement>(`[data-copy="${CSS.escape(id)}"]`)?.focus();
	}
</script>

<div class="page">
	<UtilityHeader title={m.utilities_duplicates()} lead={m.utilities_duplicates_lead()}>
		{#snippet tools()}
			{#if all.length > 0}
				<button type="button" class="btn" onclick={() => (marked = keepInAll(all, oldest))}>
					<Icon path={magicPath} size={18} />
					{m.utilities_keep_oldest()}
				</button>
				<button type="button" class="btn" onclick={() => (marked = keepInAll(all, largest))}>
					{m.utilities_keep_largest()}
				</button>
				<button
					type="button"
					class="btn"
					disabled={marked.size === 0}
					onclick={() => (marked = new Set())}
				>
					{m.utilities_unmark_all()}
				</button>
			{/if}
		{/snippet}
		{#snippet actions()}
			{#if all.length > 0}
				<button
					type="button"
					class="btn danger"
					disabled={marked.size === 0 || busy}
					onclick={() => (confirming = true)}
				>
					<Icon name="delete" size={18} />
					{marked.size ? m.utilities_trash_marked({ count: marked.size }) : m.utilities_trash()}
				</button>
			{/if}
		{/snippet}
	</UtilityHeader>

	{#if groups === null && !failed}
		<Skeleton variant="cards" count={8} />
	{:else if failed}
		<div role="alert"><EmptyState icon="info" title={m.error_loading()} /></div>
	{:else if all.length === 0}
		<EmptyState iconPath={duplicatesPath} title={m.utilities_duplicates_empty()} />
	{:else}
		<p class="summary" aria-live="polite">
			{m.utilities_duplicates_summary({ count: all.length, size: formatBytes(recoverable) })}
			{#if marked.size > 0}
				· <strong
					>{m.utilities_marked_summary({
						count: marked.size,
						size: formatBytes(selectedBytes)
					})}</strong
				>
			{/if}
		</p>
		<ol class="groups">
			{#each all as group, index (group.hash)}
				{@const folders = splitFolders(group.assets.map((asset) => directoryOf(asset.fullPath)))}
				<li>
					<section aria-label={m.utilities_group_label({ index: index + 1 })}>
						<h2>
							{m.utilities_group_title({
								count: group.assets.length,
								size: formatBytes(group.assets[0]?.fileSize ?? 0)
							})}
						</h2>
						<ul class="copies">
							{#each group.assets as asset, i (asset.id)}
								{@const out = marked.has(asset.id)}
								<li class:out>
									<button
										type="button"
										class="thumb"
										data-copy={asset.id}
										aria-label={m.utilities_open_copy({ name: asset.fileName })}
										onclick={() => viewer.open(asset.id)}
									>
										<img
											src={thumbnailUrl(asset.id, 'Small', asset.thumbnailsGeneratedAt)}
											alt=""
											loading="lazy"
										/>
										<span class="badge"
											>{out ? m.utilities_badge_trash() : m.utilities_badge_keep()}</span
										>
									</button>
									<div class="meta">
										<span class="name" title={asset.fileName}>{asset.fileName}</span>
										<!-- The whole path: what sets this copy apart is in it. -->
										<span class="folder" title={asset.fullPath}
											><span class="common">{folders[i].common}</span>{#if folders[i].own}<strong
													>{folders[i].own}</strong
												>{/if}</span
										>
										<span class="details">
											{[
												dateTime(asset.fileCreatedAt),
												formatBytes(asset.fileSize),
												asset.width && asset.height ? `${asset.width}×${asset.height}` : null
											]
												.filter(Boolean)
												.join(' · ')}
										</span>
									</div>
									<div class="choices">
										<label>
											<input
												type="checkbox"
												checked={out}
												aria-label={m.utilities_mark_copy({ copy: copyLabel(asset) })}
												onchange={(event) => {
													const next = toggleCopy(marked, group, asset.id);
													if (next.size === marked.size && !out) {
														event.currentTarget.checked = false;
														toasts.show(m.utilities_keep_one());
													}
													marked = next;
												}}
											/>
											{m.utilities_to_trash()}
										</label>
										<button
											type="button"
											class="btn sm"
											aria-label={m.utilities_keep_only_copy({ copy: copyLabel(asset) })}
											onclick={() => (marked = keepOnly(marked, group, asset.id))}
										>
											{m.utilities_keep_only()}
										</button>
									</div>
								</li>
							{/each}
						</ul>
					</section>
				</li>
			{/each}
		</ol>
	{/if}
</div>

<ViewerHost
	{viewer}
	{order}
	versionOf={(id) => byId[id]?.thumbnailsGeneratedAt}
	ontrash={(id) => trash([id])}
	onclosed={focusCopy}
/>

<Dialog
	open={confirming}
	title={m.utilities_confirm_trash_title()}
	onclose={() => (confirming = false)}
>
	<p>
		{m.utilities_confirm_trash_body({ count: marked.size, size: formatBytes(selectedBytes) })}
	</p>
	{#snippet actions()}
		<button type="button" onclick={() => (confirming = false)}>{m.utilities_cancel()}</button>
		<button
			type="button"
			class="danger"
			onclick={() => {
				confirming = false;
				trash([...marked]);
			}}
		>
			{m.utilities_trash_marked({ count: marked.size })}
		</button>
	{/snippet}
</Dialog>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	.summary {
		margin: 0;
		padding: 0 var(--page-gutter) var(--space-3);
		color: var(--color-text-muted);
	}

	.summary strong {
		color: var(--color-danger);
	}

	.groups {
		display: grid;
		gap: var(--space-3);
		margin: 0;
		padding: 0 var(--page-gutter);
		list-style: none;
	}

	.groups > li {
		content-visibility: auto;
		contain-intrinsic-size: auto 280px;
		padding: var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
	}

	h2 {
		margin: 0 0 var(--space-3);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	/* One copy per row: the thumbnail, its whole path and the choices side by
	   side, so the copies of a group read as a list to compare. */
	.groups > li {
		container-type: inline-size;
	}

	.copies {
		display: grid;
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.copies > li {
		display: grid;
		grid-template-columns: 128px minmax(0, 1fr) auto;
		gap: var(--space-4);
		align-items: center;
		padding: var(--space-3) 0;
	}

	.copies > li + li {
		border-top: 1px solid var(--color-border);
	}

	.copies > li:first-child {
		padding-top: 0;
	}

	.copies > li:last-child {
		padding-bottom: 0;
	}

	.thumb {
		position: relative;
		display: block;
		aspect-ratio: 4 / 3;
		padding: 0;
		overflow: hidden;
		border: 2px solid transparent;
		border-radius: var(--radius-sm);
		background: var(--color-placeholder);
		cursor: zoom-in;
	}

	.thumb img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: opacity var(--duration-fast);
	}

	.badge {
		position: absolute;
		left: var(--space-1);
		bottom: var(--space-1);
		padding: 1px var(--space-2);
		border-radius: 999px;
		background: rgb(0 0 0 / 0.6);
		color: #fff;
		font-size: var(--font-size-2xs);
		font-weight: 600;
	}

	.out .thumb {
		border-color: var(--color-danger);
	}

	.out .thumb img {
		opacity: 0.45;
	}

	.out .badge {
		background: var(--color-danger);
	}

	.meta {
		display: grid;
		gap: 2px;
		min-width: 0;
		font-size: var(--font-size-sm);
	}

	.name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-weight: 600;
	}

	.folder {
		overflow-wrap: anywhere;
	}

	.folder .common,
	.details {
		color: var(--color-text-muted);
	}

	.folder strong {
		font-weight: 600;
	}

	.details {
		font-size: var(--font-size-xs);
	}

	.out .name,
	.out .folder strong {
		text-decoration: line-through;
		text-decoration-color: var(--color-danger);
	}

	/* Narrow: the choices go under the photo and its path. */
	@container (max-width: 560px) {
		.copies > li {
			grid-template-columns: 96px minmax(0, 1fr);
			gap: var(--space-2) var(--space-3);
		}

		.choices {
			grid-column: 1 / -1;
		}
	}

	.choices {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: flex-end;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.choices label {
		white-space: nowrap;
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		cursor: pointer;
	}

	.choices input {
		accent-color: var(--color-danger);
	}
</style>
