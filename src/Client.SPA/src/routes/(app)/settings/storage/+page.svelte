<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getStorageInfoOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { quotaLevel } from '#lib/admin/users.js';
	import { formatBytes } from '#lib/format.js';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { hiddenSpaces } from '#lib/storage/hidden-spaces.svelte.js';
	import SpaceBar from '#lib/storage/SpaceBar.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const storage = createQuery(() => getStorageInfoOptions());
	const info = $derived(storage.data);

	const fraction = $derived(
		info?.quotaBytes ? Math.min(1, info.usedBytes / Math.max(1, info.quotaBytes)) : null
	);
	const level = $derived(info ? quotaLevel(info.usedBytes, info.quotaBytes) : null);
	// The real share, past 100 % when the quota is exceeded (the bar stops at full).
	const percent = $derived(
		fraction === null || !info?.quotaBytes
			? ''
			: new Intl.NumberFormat(getLocale(), { style: 'percent', maximumFractionDigits: 0 }).format(
					info.usedBytes / info.quotaBytes
				)
	);
	const count = (n: number) => new Intl.NumberFormat(getLocale()).format(n);

	// One row per space: personal (what the quota counts), shared folders and
	// each external library. Hidden spaces stay listed but leave the total.
	interface Row {
		key: string;
		space: 'personal' | 'shared' | 'library';
		name: string;
		photos: number;
		videos: number;
		bytes: number;
	}
	const rows = $derived.by((): Row[] => {
		if (!info) return [];
		const list: Row[] = [
			{
				key: 'personal',
				space: 'personal',
				name: m.settings_storage_personal(),
				photos: info.personalPhotos,
				videos: info.personalVideos,
				bytes: info.personalPhotoBytes + info.personalVideoBytes
			}
		];
		if (info.sharedPhotos + info.sharedVideos > 0)
			list.push({
				key: 'shared',
				space: 'shared',
				name: m.storage_space_shared(),
				photos: info.sharedPhotos,
				videos: info.sharedVideos,
				bytes: info.sharedPhotoBytes + info.sharedVideoBytes
			});
		for (const library of info.libraries)
			list.push({
				key: library.id,
				space: 'library',
				name: library.name,
				photos: library.photos,
				videos: library.videos,
				bytes: library.photoBytes + library.videoBytes
			});
		return list;
	});
	const isHidden = (row: Row) => row.space !== 'personal' && hiddenSpaces.has(row.space);
	const shown = $derived(rows.filter((row) => !isHidden(row)));
	const spaceBytes = $derived({
		personal: rows.filter((r) => r.space === 'personal').reduce((sum, r) => sum + r.bytes, 0),
		shared: rows.filter((r) => r.space === 'shared').reduce((sum, r) => sum + r.bytes, 0),
		library: rows.filter((r) => r.space === 'library').reduce((sum, r) => sum + r.bytes, 0)
	});
	const notCounted = $derived(
		[
			spaceBytes.shared > 0
				? m.settings_storage_in_shared({ size: formatBytes(spaceBytes.shared) })
				: null,
			spaceBytes.library > 0
				? m.settings_storage_in_libraries({ size: formatBytes(spaceBytes.library) })
				: null
		]
			.filter(Boolean)
			.join(' · ')
	);
</script>

<svelte:head>
	<title>{m.settings_storage()} · {m.app_name()}</title>
</svelte:head>

<div class="settings-page">
	<PageHeader title={m.settings_storage()} subtitle={m.settings_storage_lead()} />

	{#if storage.isPending}
		<Skeleton variant="rows" count={4} />
	{:else if !info}
		<div class="settings-body"><p class="error" role="alert">{m.error_loading()}</p></div>
	{:else}
		<div class="settings-body">
			<section class="card" aria-labelledby="usage-title">
				<header>
					<h2 id="usage-title">{m.settings_storage_usage()}</h2>
				</header>
				<p class="used">
					{#if info.quotaBytes}
						{m.settings_storage_used_of({
							used: formatBytes(info.usedBytes),
							quota: formatBytes(info.quotaBytes)
						})}
					{:else}
						{m.settings_storage_used({ used: formatBytes(info.usedBytes) })}
					{/if}
				</p>
				{#if fraction !== null}
					<div
						class="meter"
						class:warn={level !== 'ok'}
						role="meter"
						aria-label={m.settings_storage_usage()}
						aria-valuemin={0}
						aria-valuemax={100}
						aria-valuenow={Math.round(fraction * 100)}
						aria-valuetext={percent}
					>
						<span style:width="{fraction * 100}%"></span>
					</div>
					<p class="hint">
						{level === 'over'
							? m.settings_storage_over_quota({ percent })
							: level === 'warning'
								? m.settings_storage_almost_full({ percent })
								: m.settings_storage_percent({ percent })}
					</p>
				{:else}
					<p class="hint">{m.settings_storage_no_quota()}</p>
				{/if}
				{#if notCounted}
					<p class="hint">{m.settings_storage_not_counted({ spaces: notCounted })}</p>
				{/if}
			</section>

			<section class="card" aria-labelledby="breakdown-title">
				<header>
					<h2 id="breakdown-title">{m.settings_storage_breakdown()}</h2>
					<p>{m.settings_storage_breakdown_lead()}</p>
				</header>
				<SpaceBar bytes={spaceBytes} label={m.settings_storage_breakdown()} />
				<table>
					<thead>
						<tr>
							<th scope="col">{m.settings_storage_source()}</th>
							<th scope="col" class="num">{m.settings_storage_photos()}</th>
							<th scope="col" class="num">{m.settings_storage_videos()}</th>
							<th scope="col" class="num">{m.settings_storage_size()}</th>
						</tr>
					</thead>
					<tbody>
						{#each rows as row (row.key)}
							<tr class:off={isHidden(row)}>
								<th scope="row"
									><i class="swatch {row.space}" class:hidden={isHidden(row)}></i>{row.name}</th
								>
								<td class="num">{count(row.photos)}</td>
								<td class="num">{count(row.videos)}</td>
								<td class="num">{formatBytes(row.bytes)}</td>
							</tr>
						{/each}
					</tbody>
					<tfoot>
						<tr>
							<th scope="row">{m.settings_storage_total()}</th>
							<td class="num">{count(shown.reduce((sum, r) => sum + r.photos, 0))}</td>
							<td class="num">{count(shown.reduce((sum, r) => sum + r.videos, 0))}</td>
							<td class="num">{formatBytes(shown.reduce((sum, r) => sum + r.bytes, 0))}</td>
						</tr>
					</tfoot>
				</table>
				<p class="hint">{m.settings_storage_quota_note()}</p>
			</section>
		</div>
	{/if}
</div>

<style>
	.used {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	.meter {
		height: 10px;
		border-radius: 5px;
		background: var(--color-placeholder);
		overflow: hidden;
	}

	.meter span {
		display: block;
		height: 100%;
		border-radius: 5px;
		background: var(--color-accent);
	}

	.meter.warn span {
		background: var(--color-danger);
	}

	table {
		width: 100%;
		border-collapse: collapse;
		font-size: var(--font-size-sm);
	}

	th,
	td {
		padding: var(--space-2) var(--space-3);
		border-bottom: 1px solid var(--color-border);
		text-align: left;
	}

	thead th {
		color: var(--color-text-muted);
		font-weight: 500;
	}

	tbody th {
		font-weight: 400;
	}

	tbody tr.off {
		color: var(--color-text-muted);
	}

	.swatch {
		display: inline-block;
		width: 10px;
		height: 10px;
		margin-right: var(--space-2);
		border-radius: 2px;
		vertical-align: baseline;
	}

	.swatch.personal {
		background: var(--color-space-personal);
	}

	.swatch.shared {
		background: var(--color-space-shared);
	}

	.swatch.library {
		background: var(--color-space-library);
	}

	.swatch.hidden {
		background: repeating-linear-gradient(
			135deg,
			var(--color-space-hidden) 0 2px,
			transparent 2px 5px
		);
		outline: 1px solid var(--color-space-hidden);
		outline-offset: -1px;
	}

	tfoot th,
	tfoot td {
		border-bottom: 0;
		font-weight: 600;
	}

	.num {
		text-align: right;
		font-variant-numeric: tabular-nums;
	}
</style>
