<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import {
		adminIndexingCoverageOptions,
		getAdminGrowthOptions,
		getAdminStatsOptions,
		getAllUsersOptions,
		getApiAdminMaintenanceMlPendingTotalOptions,
		getTrashStatsOptions,
		getVersionOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AdminPage from '#lib/admin/AdminPage.svelte';
	import { count, localDateTime, percent } from '#lib/admin/format.js';
	import GrowthChart from '#lib/admin/GrowthChart.svelte';
	import { adminIcons } from '#lib/admin/icons.js';
	import { coverageShare, coverageTone, growthSeries, type Tone } from '#lib/admin/stats.js';
	import Icon, { type IconName } from '#lib/components/Icon.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { formatBytes } from '#lib/format.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';

	const statsQuery = createQuery(() => getAdminStatsOptions());
	const growthQuery = createQuery(() => getAdminGrowthOptions());
	const usersQuery = createQuery(() => getAllUsersOptions());
	// Operational widgets are best effort: each one hides its numbers on failure.
	const versionQuery = createQuery(() => ({ ...getVersionOptions(), retry: false }));
	const mlQuery = createQuery(() => ({
		...getApiAdminMaintenanceMlPendingTotalOptions(),
		retry: false
	}));
	const trashQuery = createQuery(() => ({ ...getTrashStatsOptions(), retry: false }));
	const coverageQuery = createQuery(() => ({ ...adminIndexingCoverageOptions(), retry: false }));

	const stats = $derived(statsQuery.data);
	const usage = $derived(
		[...(stats?.users ?? [])]
			.map((u) => ({ ...u, bytes: u.photoBytes + u.videoBytes }))
			.sort((a, b) => b.bytes - a.bytes)
	);
	const photoBytes = $derived(usage.reduce((sum, u) => sum + u.photoBytes, 0));
	const videoBytes = $derived(usage.reduce((sum, u) => sum + u.videoBytes, 0));
	const months = $derived(growthSeries(growthQuery.data ?? [], 24));
	const coverage = $derived(coverageQuery.data);
	const share = $derived(coverage?.hasResult ? coverageShare(coverage) : null);
	const tone = $derived(share !== null ? coverageTone(share) : null);
	const admins = $derived((usersQuery.data ?? []).filter((u) => u.role === 'Admin').length);

	/** One card design for every number on the dashboard. */
	interface Metric {
		label: string;
		value: string;
		sub: string;
		icon?: IconName;
		iconPath?: string;
		/** The section that explains the number. */
		href?: string;
		badge?: string;
		tone?: Tone;
		/** Still loading: a placeholder instead of the dash. */
		pending?: boolean;
	}
	let showUnindexed = $state(false);

	const dash = '—';

	const links: {
		path: string;
		icon: IconName | null;
		iconPath?: string;
		title: () => string;
		text: () => string;
	}[] = [
		{ path: '/admin/users', icon: 'people', title: m.admin_users, text: m.admin_dash_link_users },
		{
			path: '/admin/libraries',
			icon: null,
			iconPath: adminIcons.folderSpecial,
			title: m.admin_libraries,
			text: m.admin_dash_link_libraries
		},
		{ path: '/admin/tasks', icon: 'history', title: m.admin_tasks, text: m.admin_dash_link_tasks },
		{
			path: '/admin/maintenance',
			icon: 'build',
			title: m.admin_maintenance,
			text: m.admin_dash_link_maintenance
		},
		{
			path: '/admin/settings',
			icon: 'settings',
			title: m.admin_settings,
			text: m.admin_dash_link_settings
		},
		{
			path: '/admin/backup',
			icon: null,
			iconPath: adminIcons.cloudDownload,
			title: m.admin_backup,
			text: m.admin_dash_link_backup
		},
		{
			path: '/admin/shared-trash',
			icon: 'restore',
			title: m.admin_shared_trash,
			text: m.admin_dash_link_shared_trash
		},
		{ path: '/admin/system', icon: 'info', title: m.admin_system, text: m.admin_dash_link_system }
	];
</script>

<AdminPage title={m.admin_dash_title()} description={m.admin_dash_description()}>
	{#if statsQuery.isError}
		<p class="notice warning" role="alert">{m.admin_dash_stats_failed()}</p>
	{/if}

	<section class="metrics" aria-label={m.admin_dash_totals()}>
		{@render metric({
			label: m.admin_dash_photos(),
			value: stats ? count(stats.totalPhotos) : dash,
			pending: statsQuery.isPending,
			icon: 'photos',
			sub: stats ? m.admin_dash_size_used({ size: formatBytes(photoBytes) }) : dash
		})}
		{@render metric({
			label: m.admin_dash_videos(),
			value: stats ? count(stats.totalVideos) : dash,
			pending: statsQuery.isPending,
			iconPath: adminIcons.videocam,
			sub: stats ? m.admin_dash_size_used({ size: formatBytes(videoBytes) }) : dash
		})}
		{@render metric({
			label: m.admin_dash_users(),
			value: usersQuery.data ? count(usersQuery.data.length) : dash,
			pending: usersQuery.isPending,
			icon: 'people',
			sub: usersQuery.data ? m.admin_dash_users_admins({ count: admins, n: count(admins) }) : dash,
			href: appHref('/admin/users')
		})}
		{@render metric({
			label: m.admin_dash_storage(),
			value: stats ? formatBytes(stats.totalBytes) : dash,
			pending: statsQuery.isPending,
			iconPath: adminIcons.storage,
			sub: stats ? m.admin_dash_storage_sub({ count: usage.length, n: count(usage.length) }) : dash
		})}
	</section>

	<section class="metrics" aria-label={m.admin_dash_status()}>
		{@render metric({
			label: m.admin_dash_version(),
			value: versionQuery.data ? `v${versionQuery.data.currentVersion}` : dash,
			pending: versionQuery.isPending,
			iconPath: adminIcons.sync,
			href: appHref('/admin/system'),
			badge: versionQuery.data?.hasUpdate ? m.admin_dash_update() : undefined,
			sub: !versionQuery.data
				? dash
				: versionQuery.data.hasUpdate
					? m.admin_dash_update_available({ version: versionQuery.data.latestVersion ?? '' })
					: versionQuery.data.isAhead
						? m.admin_dash_version_ahead({ version: versionQuery.data.latestVersion ?? '' })
						: versionQuery.data.checkError
							? m.admin_dash_version_unchecked()
							: m.admin_dash_up_to_date()
		})}
		{@render metric({
			label: m.admin_dash_ml_queue(),
			value: mlQuery.data ? count(mlQuery.data.count) : dash,
			pending: mlQuery.isPending,
			iconPath: adminIcons.psychology,
			href: appHref('/admin/tasks'),
			sub: mlQuery.data
				? mlQuery.data.count > 0
					? m.admin_dash_ml_pending()
					: m.admin_dash_ml_done()
				: dash
		})}
		{@render metric({
			label: m.admin_dash_trash(),
			value: trashQuery.data ? count(trashQuery.data.totalItems) : dash,
			pending: trashQuery.isPending,
			icon: 'delete',
			href: appHref('/admin/maintenance'),
			badge: trashQuery.data?.expiredItems
				? m.admin_dash_trash_expired({
						count: trashQuery.data.expiredItems,
						n: count(trashQuery.data.expiredItems)
					})
				: undefined,
			sub: trashQuery.data
				? m.admin_dash_trash_size({ size: formatBytes(trashQuery.data.totalBytes) })
				: dash
		})}
		{@render metric({
			label: m.admin_dash_coverage(),
			value: share !== null ? percent(share, 1) : dash,
			pending: coverageQuery.isPending,
			iconPath: adminIcons.checkCircle,
			href: '#coverage',
			tone: tone ?? undefined,
			sub: coverage?.hasResult
				? coverage.unindexed > 0
					? m.admin_dash_coverage_unindexed({
							count: coverage.unindexed,
							n: count(coverage.unindexed)
						})
					: m.admin_dash_coverage_complete()
				: coverage
					? m.admin_dash_coverage_never_short()
					: dash
		})}
	</section>

	<div class="charts">
		<section class="card growth" aria-labelledby="growth-title">
			<h2 id="growth-title">{m.admin_dash_growth()}</h2>
			<p class="muted small sub">{m.admin_dash_growth_sub()}</p>
			{#if growthQuery.isSuccess}
				<GrowthChart {months} label={m.admin_dash_growth()} />
			{:else if growthQuery.isError}
				<p class="muted">{m.error_loading()}</p>
			{:else}
				<Skeleton variant="text" count={4} />
			{/if}
		</section>

		<section class="card" aria-labelledby="split-title">
			<h2 id="split-title">{m.admin_dash_split()}</h2>
			{#if stats}
				{@render split(
					m.admin_dash_split_items(),
					stats.totalPhotos,
					stats.totalVideos,
					count(stats.totalPhotos),
					count(stats.totalVideos)
				)}
				{@render split(
					m.admin_dash_split_storage(),
					photoBytes,
					videoBytes,
					formatBytes(photoBytes),
					formatBytes(videoBytes)
				)}
				<h3>{m.admin_dash_top_users()}</h3>
				<ol class="top">
					{#each usage.slice(0, 5) as user (user.userId)}
						<li>
							<span class="top-name">{user.displayName}</span>
							<span class="num muted small">{formatBytes(user.bytes)}</span>
							<span class="bar"
								><span style:width="{usage[0].bytes ? (user.bytes / usage[0].bytes) * 100 : 0}%"
								></span></span
							>
						</li>
					{:else}
						<li class="muted">{m.admin_dash_no_usage()}</li>
					{/each}
				</ol>
			{:else}
				<Skeleton variant="text" count={4} />
			{/if}
		</section>
	</div>

	<section class="card" id="coverage" aria-labelledby="coverage-title" tabindex="-1">
		<h2 id="coverage-title">{m.admin_dash_coverage_title()}</h2>
		{#if coverageQuery.isError}
			<p class="muted">{m.admin_dash_coverage_unavailable()}</p>
		{:else if coverage && !coverage.hasResult}
			<p class="muted">{m.admin_dash_coverage_never()}</p>
		{:else if coverage}
			<div class="coverage">
				<dl class="facts">
					<div>
						<dt>{m.admin_dash_coverage_total()}</dt>
						<dd class="num">{count(coverage.totalFiles)}</dd>
					</div>
					<div>
						<dt>{m.admin_dash_coverage_indexed()}</dt>
						<dd class="num">{count(coverage.indexed)}</dd>
					</div>
					<div>
						<dt>{m.admin_dash_coverage_unsupported()}</dt>
						<dd class="num">{count(coverage.unsupported)}</dd>
					</div>
					<div>
						<dt>{m.admin_dash_coverage_unindexed_label()}</dt>
						<dd class="num {coverage.unindexed > 0 ? tone : ''}">{count(coverage.unindexed)}</dd>
					</div>
				</dl>
				<div class="coverage-summary">
					<div
						class="bar {tone ?? ''}"
						role="meter"
						aria-valuemin="0"
						aria-valuemax="100"
						aria-valuenow={Math.round((share ?? 0) * 100)}
						aria-label={m.admin_dash_coverage()}
					>
						<span style:width="{(share ?? 0) * 100}%"></span>
					</div>
					<p class="small">
						{coverage.unindexed === 0
							? m.admin_dash_coverage_summary_complete({
									count: count(coverage.indexed + coverage.unindexed)
								})
							: m.admin_dash_coverage_summary_partial({
									indexed: count(coverage.indexed),
									total: count(coverage.indexed + coverage.unindexed)
								})}
					</p>
					{#if coverage.offlineLibraries > 0}
						<p class="small danger">
							{m.admin_dash_coverage_offline({ count: coverage.offlineLibraries })}
						</p>
					{/if}
					{#if coverage.verifiedAtUtc}
						<p class="muted small">
							{m.admin_dash_coverage_verified({ date: localDateTime(coverage.verifiedAtUtc) })}
						</p>
					{/if}
				</div>
			</div>
			{#if coverage.unindexedPaths.length}
				<button
					type="button"
					class="btn"
					aria-expanded={showUnindexed}
					aria-controls="unindexed-list"
					onclick={() => (showUnindexed = !showUnindexed)}
				>
					{showUnindexed
						? m.admin_dash_coverage_hide_list()
						: m.admin_dash_coverage_show_list({
								count: coverage.unindexed,
								n: count(coverage.unindexed)
							})}
				</button>
				{#if showUnindexed}
					<ul id="unindexed-list" class="paths">
						{#each coverage.unindexedPaths as path (path)}<li><code>{path}</code></li>{/each}
					</ul>
					{#if coverage.unindexedTruncated}
						<p class="muted small">
							{m.admin_dash_coverage_truncated({ count: coverage.unindexedPaths.length })}
						</p>
					{/if}
				{/if}
			{/if}
		{:else}
			<Skeleton variant="text" count={2} />
		{/if}
	</section>

	{#if usage.length}
		<section aria-labelledby="usage-title" class="usage">
			<h2 id="usage-title">{m.admin_dash_usage_title()}</h2>
			<div class="table-wrap">
				<table class="data">
					<thead>
						<tr>
							<th scope="col">{m.admin_users_col_user()}</th>
							<th scope="col" class="right">{m.admin_dash_photos()}</th>
							<th scope="col" class="right">{m.admin_dash_videos()}</th>
							<th scope="col" class="right">{m.admin_dash_storage()}</th>
							<th scope="col">{m.admin_dash_share()}</th>
						</tr>
					</thead>
					<tbody>
						{#each usage as user (user.userId)}
							<tr>
								<td>
									<strong>{user.displayName}</strong>
									{#if user.email}<span class="muted small"> · {user.email}</span>{/if}
								</td>
								<td class="right num">
									{count(user.photos)}
									<span class="muted small">({formatBytes(user.photoBytes)})</span>
								</td>
								<td class="right num">
									{count(user.videos)}
									<span class="muted small">({formatBytes(user.videoBytes)})</span>
								</td>
								<td class="right num">{formatBytes(user.bytes)}</td>
								<td class="share">
									<div class="share-cell">
										<span class="bar"
											><span
												style:width="{stats?.totalBytes
													? (user.bytes / stats.totalBytes) * 100
													: 0}%"
											></span></span
										>
										<span class="num muted small"
											>{percent(stats?.totalBytes ? user.bytes / stats.totalBytes : 0)}</span
										>
									</div>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</section>
	{/if}

	<section aria-labelledby="links-title">
		<h2 id="links-title">{m.admin_dash_links()}</h2>
		<ul class="links">
			{#each links as link (link.path)}
				<li>
					<a class="card link" href={appHref(link.path)}>
						<span class="link-icon" aria-hidden="true"
							><Icon name={link.icon ?? undefined} path={link.iconPath} size={20} /></span
						>
						<span>
							<strong>{link.title()}</strong>
							<span class="muted small">{link.text()}</span>
						</span>
					</a>
				</li>
			{/each}
		</ul>
	</section>
</AdminPage>

{#snippet metric(card: Metric)}
	<svelte:element this={card.href ? 'a' : 'div'} class="card metric" href={card.href}>
		<span class="metric-icon" aria-hidden="true"
			><Icon name={card.icon} path={card.iconPath} size={22} /></span
		>
		<span class="metric-body">
			<span class="metric-label">
				{card.label}
				{#if card.badge}<span class="chip tag warning">{card.badge}</span>{/if}
			</span>
			{#if card.value === dash && card.pending}
				<span class="metric-value pending" role="status" aria-label={m.loading()}></span>
			{:else}
				<strong class="metric-value num {card.tone ?? ''}">{card.value}</strong>
			{/if}
			<span class="metric-sub">{card.sub}</span>
		</span>
	</svelte:element>
{/snippet}

{#snippet split(
	label: string,
	photos: number,
	videos: number,
	photosText: string,
	videosText: string
)}
	{@const total = photos + videos}
	<div class="split">
		<span class="small">{label}</span>
		<div class="split-bar" aria-hidden="true">
			<span class="photos" style:flex-grow={total ? photos : 1}></span>
			<span class="videos" style:flex-grow={total ? videos : 0}></span>
		</div>
		<div class="split-legend small">
			<span
				><i class="photos"></i>{m.admin_dash_photos()}
				<span class="num muted">{photosText}</span></span
			>
			<span
				><i class="videos"></i>{m.admin_dash_videos()}
				<span class="num muted">{videosText}</span></span
			>
		</div>
	</div>
{/snippet}

<style>
	h2 {
		margin: 0 0 var(--space-2);
		font-size: var(--font-size-md);
	}

	h3 {
		margin: var(--space-4) 0 var(--space-2);
		font-size: var(--font-size-sm);
	}

	p {
		margin: 0;
	}

	.metrics {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: var(--space-3);
	}

	/* One card for every number: icon tile, label (and a tag), value, a line. */
	.metric {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
	}

	.metric-icon {
		flex: none;
		display: grid;
		place-items: center;
		width: 44px;
		height: 44px;
		border-radius: var(--radius-control);
		background: var(--color-brand-tile);
		color: var(--color-accent);
	}

	.metric-body {
		flex: 1;
		display: grid;
		gap: 2px;
		min-width: 0;
	}

	.metric-label {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-1) var(--space-2);
		min-height: 24px;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.metric-value {
		font-size: var(--font-size-xl);
		font-weight: 650;
		line-height: 1.2;
	}

	.metric-value.pending {
		width: 6ch;
		height: 1.2em;
		border-radius: var(--radius-sm);
		background: var(--color-placeholder);
	}

	.metric-sub {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.success {
		color: var(--color-success);
	}

	.warning {
		color: var(--color-warning);
	}

	.danger {
		color: var(--color-danger);
	}

	.charts {
		display: grid;
		grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
		gap: var(--space-3);
	}

	.sub {
		margin-bottom: var(--space-3);
	}

	.split {
		display: grid;
		gap: var(--space-1);
		margin-bottom: var(--space-3);
	}

	.split-bar {
		display: flex;
		gap: 2px;
		height: 12px;
		overflow: hidden;
		border-radius: 999px;
		background: var(--color-placeholder);
	}

	.split-bar span {
		flex-basis: 0;
		min-width: 0;
	}

	.photos {
		background: var(--admin-chart-photos);
	}

	.videos {
		background: var(--admin-chart-videos);
	}

	.split-legend {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1) var(--space-4);
	}

	.split-legend span {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
	}

	.split-legend i {
		width: 10px;
		height: 10px;
		border-radius: 2px;
	}

	.top {
		display: grid;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.top li {
		display: grid;
		grid-template-columns: 1fr auto;
		gap: 2px var(--space-2);
		font-size: var(--font-size-sm);
	}

	.top .bar {
		grid-column: 1 / -1;
	}

	.top-name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	#coverage {
		scroll-margin-top: 64px;
	}

	.coverage {
		display: grid;
		grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
		gap: var(--space-4);
		margin-bottom: var(--space-3);
	}

	.facts {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: var(--space-3);
		margin: 0;
	}

	.facts dt {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	.facts dd {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	.coverage-summary {
		display: grid;
		gap: var(--space-2);
		align-content: start;
	}

	.paths {
		max-height: 240px;
		overflow: auto;
		margin: var(--space-3) 0 var(--space-2);
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		list-style: none;
		font-size: var(--font-size-xs);
	}

	.right {
		text-align: right !important;
	}

	.share {
		min-width: 160px;
	}

	.share-cell {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.share-cell .bar {
		flex: 1;
	}

	.links {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
		gap: var(--space-3);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.link {
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
		height: 100%;
	}

	.link > span:last-child {
		display: grid;
		gap: 2px;
	}

	.link-icon {
		flex: none;
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border-radius: var(--radius-control);
		background: var(--color-brand-tile);
		color: var(--color-accent);
	}

	/* Four across only while a card keeps its label and tag on one line. */
	@media (max-width: 1360px) {
		.metrics {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}

	@media (max-width: 1100px) {
		.charts,
		.coverage {
			grid-template-columns: minmax(0, 1fr);
		}
	}
</style>
