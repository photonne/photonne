<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getStorageInfoOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { formatBytes } from '#lib/format.js';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const storage = createQuery(() => getStorageInfoOptions());
	const info = $derived(storage.data);

	const fraction = $derived(
		info?.quotaBytes ? Math.min(1, info.usedBytes / Math.max(1, info.quotaBytes)) : null
	);
	const percent = $derived(
		fraction === null
			? ''
			: new Intl.NumberFormat(getLocale(), { style: 'percent', maximumFractionDigits: 0 }).format(
					fraction
				)
	);
	const count = (n: number) => new Intl.NumberFormat(getLocale()).format(n);
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
						class:warn={fraction >= 0.9}
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
						{fraction >= 0.9
							? m.settings_storage_almost_full({ percent })
							: m.settings_storage_percent({ percent })}
					</p>
				{:else}
					<p class="hint">{m.settings_storage_no_quota()}</p>
				{/if}
			</section>

			<section class="card" aria-labelledby="breakdown-title">
				<header>
					<h2 id="breakdown-title">{m.settings_storage_breakdown()}</h2>
					<p>{m.settings_storage_breakdown_lead()}</p>
				</header>
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
						<tr>
							<th scope="row">{m.settings_storage_personal()}</th>
							<td class="num">{count(info.personalPhotos)}</td>
							<td class="num">{count(info.personalVideos)}</td>
							<td class="num">{formatBytes(info.personalPhotoBytes + info.personalVideoBytes)}</td>
						</tr>
						{#each info.libraries as library (library.id)}
							<tr>
								<th scope="row">{library.name}</th>
								<td class="num">{count(library.photos)}</td>
								<td class="num">{count(library.videos)}</td>
								<td class="num">{formatBytes(library.photoBytes + library.videoBytes)}</td>
							</tr>
						{/each}
					</tbody>
					<tfoot>
						<tr>
							<th scope="row">{m.settings_storage_total()}</th>
							<td class="num">{count(info.photos)}</td>
							<td class="num">{count(info.videos)}</td>
							<td class="num">{formatBytes(info.photoBytes + info.videoBytes)}</td>
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
