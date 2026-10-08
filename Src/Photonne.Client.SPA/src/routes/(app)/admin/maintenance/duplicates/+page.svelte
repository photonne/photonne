<script lang="ts">
	import { deletePhysicalDuplicates, detectDuplicatesStream } from '#lib/api/index.js';
	import '#lib/adminops/adminops.css';
	import ConfirmDialog from '#lib/adminops/ConfirmDialog.svelte';
	import ProgressBar from '#lib/adminops/ProgressBar.svelte';
	import {
		autoSelect,
		toDelete,
		toggle,
		toReview,
		withoutDeleted,
		type DuplicateReview
	} from '#lib/adminops/duplicates.js';
	import { Clock, toastError } from '#lib/adminops/feedback.svelte.js';
	import { icons } from '#lib/adminops/icons.js';
	import { stat, type ProgressEvent } from '#lib/adminops/progress.js';
	import { follow } from '#lib/adminops/streams.js';
	import { count, duration, localDateTime, percent } from '#lib/adminops/time.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { formatBytes } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	type Mode = 'analyze' | 'cleanup' | 'physical';

	const clock = new Clock();
	$effect(() => clock.start(1000));

	let mode = $state<Mode>('analyze');
	let runMode = $state<Mode>('analyze');
	let controller = $state<AbortController | null>(null);
	let last = $state<ProgressEvent | null>(null);
	let startedAt = $state<number | null>(null);
	let finishedAt = $state<number | null>(null);
	let reviews = $state<DuplicateReview[]>([]);
	let confirmCleanup = $state(false);
	let confirmDelete = $state(false);
	let deleting = $state(false);

	const running = $derived(controller !== null);
	const pendingDelete = $derived(toDelete(reviews));
	const reclaim = $derived(pendingDelete.reduce((sum, file) => sum + file.fileSize, 0));

	// The scan is tied to this connection: leaving the page stops it.
	$effect(() => () => controller?.abort());

	function requestStart() {
		if (mode === 'cleanup') confirmCleanup = true;
		else start();
	}

	async function start() {
		confirmCleanup = false;
		const abort = new AbortController();
		controller = abort;
		runMode = mode;
		last = null;
		reviews = [];
		startedAt = Date.now();
		finishedAt = null;
		try {
			const end = await follow(
				(signal) =>
					detectDuplicatesStream({
						query: { cleanup: mode === 'cleanup', physical: mode === 'physical' },
						parseAs: 'stream',
						signal
					}),
				(event) => {
					last = event;
					const review = event.foundGroup ? toReview(event.foundGroup) : null;
					if (review) reviews = [...reviews, review];
				},
				abort.signal
			);
			if (abort.signal.aborted) toasts.show(m.ops_dup_stopped());
			else if (end?.isCompleted) toasts.show(end.message || m.ops_dup_done());
		} catch (error) {
			toastError(error);
		} finally {
			controller = null;
			finishedAt = Date.now();
		}
	}

	function setReview(index: number, review: DuplicateReview) {
		reviews[index] = review;
	}

	async function remove() {
		const files = pendingDelete;
		deleting = true;
		const { data, error } = await deletePhysicalDuplicates({
			body: files.map((file) => ({ physicalPath: file.physicalPath, assetId: file.assetId }))
		});
		deleting = false;
		confirmDelete = false;
		if (error || !data) return toastError(error);
		reviews = withoutDeleted(reviews, new Set(files.map((file) => file.physicalPath)));
		if (data.errors.length) {
			toasts.error(m.ops_dup_deleted_errors({ count: data.deleted, errors: data.errors.length }));
		} else toasts.show(m.ops_dup_deleted({ count: data.deleted }));
	}

	const elapsed = $derived(startedAt === null ? null : (finishedAt ?? clock.now) - startedAt);

	const tiles = $derived.by(() => {
		if (!last?.statistics) return [];
		const list: { label: string; value: string; tone?: 'warn' | 'ok' | 'info' }[] = [
			{
				label: runMode === 'physical' ? m.ops_dup_files_on_disk() : m.ops_dup_assets_in_db(),
				value: count(stat(last, 'totalAssets') ?? 0)
			},
			{ label: m.ops_dup_groups(), value: count(stat(last, 'duplicateGroups') ?? 0), tone: 'warn' },
			{ label: m.ops_dup_copies(), value: count(stat(last, 'duplicateAssets') ?? 0), tone: 'warn' }
		];
		const bytes = stat(last, 'bytesReclaimed') ?? 0;
		if (bytes > 0) list.push({ label: m.ops_dup_bytes(), value: formatBytes(bytes) });
		if (runMode === 'physical')
			list.push({
				label: m.ops_dup_unindexed(),
				value: count(stat(last, 'unindexedFiles') ?? 0),
				tone: 'info'
			});
		if (runMode === 'cleanup')
			list.push({
				label: m.ops_dup_removed(),
				value: count(stat(last, 'removed') ?? 0),
				tone: 'ok'
			});
		return list;
	});

	const modes: { value: Mode; label: () => string; hint: () => string }[] = [
		{ value: 'analyze', label: m.ops_dup_mode_analyze, hint: m.ops_dup_mode_analyze_hint },
		{ value: 'cleanup', label: m.ops_dup_mode_cleanup, hint: m.ops_dup_mode_cleanup_hint },
		{ value: 'physical', label: m.ops_dup_mode_physical, hint: m.ops_dup_mode_physical_hint }
	];
</script>

<svelte:head>
	<title>{m.ops_mt_duplicates()} · {m.app_name()}</title>
</svelte:head>

<div class="ops-page">
	<header class="ops-head">
		<div>
			<a class="back" href={appHref('/admin/maintenance')}>
				<Icon name="chevronLeft" size={18} />
				{m.ops_mt_title()}
			</a>
			<h1>{m.ops_mt_duplicates()}</h1>
			<p>{m.ops_dup_intro()}</p>
		</div>
	</header>

	<section class="ops-card setup" aria-labelledby="mode-heading">
		<h2 id="mode-heading" class="visually-hidden">{m.ops_dup_mode()}</h2>
		<fieldset disabled={running}>
			<legend>{m.ops_dup_mode()}</legend>
			{#each modes as option (option.value)}
				<label class="mode" class:selected={mode === option.value}>
					<input type="radio" name="mode" value={option.value} bind:group={mode} />
					<span>
						<strong>{option.label()}</strong>
						<span class="ops-muted ops-small">{option.hint()}</span>
					</span>
				</label>
			{/each}
		</fieldset>
		<div class="run">
			{#if running}
				<button type="button" class="ops-btn danger" onclick={() => controller?.abort()}>
					<Icon path={icons.stop} size={18} />
					{m.ops_stop()}
				</button>
				<span class="ops-small ops-muted">{m.ops_dup_leave_stops()}</span>
			{:else}
				<button
					type="button"
					class="ops-btn {mode === 'cleanup' ? 'danger' : 'primary'}"
					onclick={requestStart}
				>
					<Icon name="play" size={18} />
					{m.ops_dup_start()}
				</button>
			{/if}
		</div>
	</section>

	{#if last || running}
		<section class="ops-section" aria-labelledby="result-heading">
			<header>
				<h2 id="result-heading">{m.ops_dup_result()}</h2>
				{#if elapsed !== null}
					<p>{m.ops_elapsed({ time: duration(elapsed) })}</p>
				{/if}
			</header>
			<div class="ops-card result" aria-live="polite">
				<div class="line">
					<ProgressBar value={last ? last.percentage : null} label={m.ops_mt_duplicates_active()} />
					<span class="pct">{last ? percent(last.percentage) : ''}</span>
				</div>
				<p class="message">{last?.message || m.ops_starting()}</p>
				{#if tiles.length}
					<div class="tiles">
						{#each tiles as tile (tile.label)}
							<div class="tile">
								<span class="value {tile.tone ?? ''}">{tile.value}</span>
								<span class="ops-muted ops-small">{tile.label}</span>
							</div>
						{/each}
					</div>
				{/if}
				{#if last?.isCompleted && (stat(last, 'duplicateGroups') ?? 0) === 0}
					<p class="ops-alert"><Icon name="check" size={18} />{m.ops_dup_none()}</p>
				{/if}
			</div>
		</section>
	{/if}

	{#if reviews.length}
		<section class="ops-section" aria-labelledby="review-heading">
			<header>
				<h2 id="review-heading">{m.ops_dup_review()}</h2>
				<p>{m.ops_dup_review_hint()}</p>
				<div class="ops-actions">
					<button
						type="button"
						class="ops-btn"
						onclick={() => (reviews = reviews.map((review) => autoSelect(review)))}
					>
						<Icon path={icons.sparkle} size={18} />
						{m.ops_dup_auto()}
					</button>
					<button
						type="button"
						class="ops-btn danger"
						disabled={pendingDelete.length === 0 || deleting || running}
						onclick={() => (confirmDelete = true)}
					>
						<Icon path={icons.deleteForever} size={18} />
						{m.ops_dup_delete({ count: pendingDelete.length, size: formatBytes(reclaim) })}
					</button>
				</div>
			</header>

			<ol class="groups">
				{#each reviews as review, index (review.hash + index)}
					{@const removing = review.files.length - review.keep.length}
					<li class="ops-card group">
						<div class="group-head">
							<h3>{m.ops_dup_group_title({ count: review.files.length })}</h3>
							{#if removing > 0}
								<span class="ops-badge warn"
									>{m.ops_dup_group_plan({ remove: removing, keep: review.keep.length })}</span
								>
							{/if}
							<code class="hash" title={review.hash}>SHA-256 {review.hash.slice(0, 12)}…</code>
							<button
								type="button"
								class="ops-btn ghost sm"
								onclick={() => setReview(index, autoSelect(review))}
							>
								{m.ops_dup_auto_group()}
							</button>
						</div>
						<ul class="files">
							{#each review.files as file (file.physicalPath)}
								{@const keep = review.keep.includes(file.physicalPath)}
								{@const lastKept = keep && review.keep.length === 1}
								<li>
									<button
										type="button"
										class="file"
										class:remove={!keep}
										aria-pressed={keep}
										aria-disabled={lastKept}
										title={lastKept ? m.ops_dup_last_copy() : undefined}
										onclick={() => !lastKept && setReview(index, toggle(review, file.physicalPath))}
									>
										<span class="thumb">
											{#if file.isIndexed && file.assetId}
												<img src={thumbnailUrl(file.assetId, 'Small')} alt="" loading="lazy" />
											{:else}
												<Icon path={icons.file} size={32} />
											{/if}
										</span>
										<span class="info">
											<span class="name">{file.fileName}</span>
											<span class="path">{file.virtualPath}</span>
											<span class="facts">
												<span>{formatBytes(file.fileSize)}</span>
												<span title={localDateTime(file.fileModifiedAt)}
													>{localDateTime(file.fileModifiedAt)}</span
												>
												{#if file.ownerUsername}<span>{file.ownerUsername}</span>{/if}
												<span class="ops-badge {file.isIndexed ? 'ok' : 'warn'}"
													>{file.isIndexed ? m.ops_dup_indexed() : m.ops_dup_not_indexed()}</span
												>
											</span>
										</span>
										<span class="verdict" class:keep>
											{keep ? m.ops_dup_keep() : m.ops_dup_remove()}
										</span>
									</button>
								</li>
							{/each}
						</ul>
					</li>
				{/each}
			</ol>
		</section>
	{/if}
</div>

<ConfirmDialog
	open={confirmCleanup}
	title={m.ops_dup_cleanup_confirm()}
	message={m.ops_dup_cleanup_confirm_text()}
	confirmLabel={m.ops_dup_start()}
	danger
	onconfirm={start}
	onclose={() => (confirmCleanup = false)}
/>

<ConfirmDialog
	open={confirmDelete}
	title={m.ops_dup_delete_confirm({ count: pendingDelete.length })}
	message={m.ops_dup_delete_confirm_text({ size: formatBytes(reclaim) })}
	confirmLabel={m.ops_dup_delete_short()}
	danger
	busy={deleting}
	onconfirm={remove}
	onclose={() => (confirmDelete = false)}
/>

<style>
	.back {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		margin-bottom: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	.back:hover {
		color: var(--color-accent);
	}

	.setup {
		display: grid;
		gap: var(--space-4);
		padding: var(--space-4);
	}

	fieldset {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
		gap: var(--space-3);
		margin: 0;
		padding: 0;
		border: none;
	}

	legend {
		margin-bottom: var(--space-2);
		font-weight: 600;
	}

	.mode {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		cursor: pointer;
	}

	.mode.selected {
		border-color: var(--color-accent);
		background: color-mix(in srgb, var(--color-accent) 6%, transparent);
	}

	.mode input {
		margin-top: 4px;
		accent-color: var(--color-accent);
	}

	.mode > span {
		display: grid;
		gap: 2px;
	}

	.run {
		display: flex;
		align-items: center;
		gap: var(--space-3);
	}

	.result {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
	}

	.line {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.line :global([role='progressbar']) {
		flex: 1;
	}

	.pct {
		min-width: 3.5em;
		font-variant-numeric: tabular-nums;
		text-align: right;
	}

	.message {
		margin: 0;
		font-size: var(--font-size-sm);
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: var(--space-3);
	}

	.tile {
		display: grid;
		padding: var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
	}

	.tile .value {
		font-size: var(--font-size-lg);
		font-weight: 700;
		font-variant-numeric: tabular-nums;
	}

	.value.warn {
		color: light-dark(#a54b00, #ffb74d);
	}

	.value.ok {
		color: light-dark(#1b5e20, #81c784);
	}

	.value.info {
		color: var(--color-accent);
	}

	.groups {
		display: grid;
		gap: var(--space-4);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.group {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
	}

	.group-head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-3);
	}

	.group-head h3 {
		margin: 0;
		font-size: var(--font-size-md);
	}

	.hash {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.group-head .ops-btn {
		margin-left: auto;
	}

	.files {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
		gap: var(--space-3);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.file {
		display: grid;
		grid-template-columns: 64px minmax(0, 1fr);
		gap: var(--space-2) var(--space-3);
		width: 100%;
		height: 100%;
		padding: var(--space-3);
		border: 2px solid var(--color-accent);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		text-align: left;
		cursor: pointer;
	}

	.file.remove {
		border-color: var(--color-danger);
		background: color-mix(in srgb, var(--color-danger) 5%, var(--color-bg));
	}

	.file.remove .thumb {
		opacity: 0.55;
	}

	.file[aria-disabled='true'] {
		cursor: default;
	}

	.thumb {
		display: grid;
		place-items: center;
		width: 64px;
		height: 64px;
		overflow: hidden;
		border-radius: var(--radius-sm);
		background: var(--color-placeholder);
		color: var(--color-text-muted);
	}

	.thumb img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.info {
		display: grid;
		gap: 2px;
		min-width: 0;
	}

	.name {
		font-weight: 600;
		word-break: break-all;
	}

	.path {
		color: var(--color-text-muted);
		font-family: ui-monospace, monospace;
		font-size: var(--font-size-xs);
		word-break: break-all;
	}

	.facts {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-1) var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.verdict {
		grid-column: 1 / -1;
		color: var(--color-danger);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.verdict.keep {
		color: var(--color-accent);
	}
</style>
