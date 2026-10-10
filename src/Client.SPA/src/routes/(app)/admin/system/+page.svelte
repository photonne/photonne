<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { getVersion } from '#lib/api/index.js';
	import {
		getAttributionsOptions,
		getDemoInfoOptions,
		getPublicVersionOptions,
		getServerInfoOptions,
		getVersionOptions,
		getVersionQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AdminPage from '#lib/admin/AdminPage.svelte';
	import { count, localDate, localDateTime, relativeTime } from '#lib/admin/format.js';
	import { adminIcons } from '#lib/admin/icons.js';
	import Icon from '#lib/components/Icon.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';

	const queryClient = useQueryClient();
	const versionQuery = createQuery(() => getVersionOptions());
	const publicQuery = createQuery(() => getPublicVersionOptions());
	const serverQuery = createQuery(() => getServerInfoOptions());
	const demoQuery = createQuery(() => getDemoInfoOptions());
	const attributionsQuery = createQuery(() => getAttributionsOptions());

	const version = $derived(versionQuery.data);
	let checking = $state(false);

	/** Asks GitHub again instead of the server's one-hour cache. */
	async function check() {
		checking = true;
		const { data } = await getVersion({ query: { refresh: true } });
		checking = false;
		if (data) queryClient.setQueryData(getVersionQueryKey(), data);
		else toasts.error(m.admin_sys_check_failed());
	}
</script>

<AdminPage title={m.admin_system()} description={m.admin_sys_description()} narrow>
	<div class="columns">
		<section class="card" aria-labelledby="version-title">
			<h2 id="version-title">{m.admin_sys_version_title()}</h2>
			{#if versionQuery.isPending}
				<Skeleton variant="text" count={3} />
			{:else if !version}
				<p class="notice danger" role="alert">{m.error_loading()}</p>
			{:else}
				<p class="current">
					<strong>v{version.currentVersion}</strong>
					{#if version.hasUpdate}
						<span class="chip tag warning">{m.admin_dash_update()}</span>
					{/if}
				</p>
				{#if version.hasUpdate}
					<p class="notice warning">
						<Icon path={adminIcons.warning} />
						<span>{m.admin_sys_update({ version: version.latestVersion ?? '' })}</span>
					</p>
				{:else if version.isAhead}
					<p class="notice">
						<Icon name="info" />
						<span>{m.admin_sys_ahead({ version: version.latestVersion ?? '' })}</span>
					</p>
				{:else if version.checkError}
					<p class="notice warning">
						<Icon path={adminIcons.warning} />
						<span>{m.admin_sys_check_error({ error: version.checkError })}</span>
					</p>
				{:else if version.latestVersion}
					<p class="notice success">
						<Icon path={adminIcons.checkCircle} />
						<span>{m.admin_sys_latest()}</span>
					</p>
				{:else}
					<p class="notice">
						<Icon name="info" />
						<span>{m.admin_sys_no_releases()}</span>
					</p>
				{/if}
				<div class="foot">
					<span class="muted small">
						{m.admin_sys_checked({ when: relativeTime(version.checkedAt) })}
					</span>
					<button type="button" class="btn" onclick={check} disabled={checking}>
						<Icon name="refresh" size={18} />
						{checking ? m.admin_sys_checking() : m.admin_sys_check()}
					</button>
				</div>
			{/if}
		</section>

		<section class="card" aria-labelledby="release-title">
			<h2 id="release-title">{m.admin_sys_release_title()}</h2>
			{#if version?.latestVersion}
				<dl class="facts">
					<dt>{m.admin_sys_release_version()}</dt>
					<dd>v{version.latestVersion}</dd>
					{#if version.publishedAt}
						<dt>{m.admin_sys_release_date()}</dt>
						<dd>{localDate(version.publishedAt)}</dd>
					{/if}
				</dl>
				{#if version.releaseNotes?.trim()}
					<h3>{m.admin_sys_release_notes()}</h3>
					<pre class="notes">{version.releaseNotes}</pre>
				{/if}
				{#if version.latestReleaseUrl}
					<div class="foot">
						<a
							class="btn"
							href={version.latestReleaseUrl}
							target="_blank"
							rel="noreferrer noopener"
						>
							{m.admin_sys_release_open()}
							<Icon path={adminIcons.openInNew} size={16} />
						</a>
					</div>
				{/if}
			{:else}
				<p class="muted">{m.admin_sys_release_none()}</p>
			{/if}
		</section>

		<section class="card" aria-labelledby="server-title">
			<h2 id="server-title">{m.admin_sys_server_title()}</h2>
			<dl class="facts">
				<dt>{m.admin_sys_server_version()}</dt>
				<dd>{publicQuery.data ? `v${publicQuery.data.version}` : '—'}</dd>
				<dt>{m.admin_sys_server_min_app()}</dt>
				<dd>
					{publicQuery.data?.minClientVersion
						? `v${publicQuery.data.minClientVersion}`
						: m.admin_sys_server_any()}
				</dd>
				<dt>{m.admin_sys_server_cpus()}</dt>
				<dd>{serverQuery.data ? count(serverQuery.data.processorCount) : '—'}</dd>
				<dt>{m.admin_sys_server_address()}</dt>
				<dd><code>{location.origin}</code></dd>
			</dl>
		</section>

		<section class="card" aria-labelledby="demo-title">
			<h2 id="demo-title">{m.admin_sys_demo_title()}</h2>
			{#if demoQuery.data?.enabled}
				<p class="notice warning">
					<Icon path={adminIcons.warning} />
					<span>{m.admin_sys_demo_on()}</span>
				</p>
				<dl class="facts">
					{#if demoQuery.data.demoUsername}
						<dt>{m.admin_sys_demo_user()}</dt>
						<dd><code>{demoQuery.data.demoUsername}</code></dd>
					{/if}
					{#if demoQuery.data.demoPassword}
						<dt>{m.admin_sys_demo_password()}</dt>
						<dd><code>{demoQuery.data.demoPassword}</code></dd>
					{/if}
					{#if demoQuery.data.resetIntervalHours}
						<dt>{m.admin_sys_demo_interval()}</dt>
						<dd>{m.admin_sys_demo_hours({ count: demoQuery.data.resetIntervalHours })}</dd>
					{/if}
					{#if demoQuery.data.nextResetAt}
						<dt>{m.admin_sys_demo_next()}</dt>
						<dd>
							<time datetime={demoQuery.data.nextResetAt}
								>{localDateTime(demoQuery.data.nextResetAt)}</time
							>
						</dd>
					{/if}
				</dl>
			{:else if demoQuery.data}
				<p class="muted">{m.admin_sys_demo_off()}</p>
			{:else}
				<p class="muted">—</p>
			{/if}
		</section>
	</div>

	<section class="card" aria-labelledby="credits-title">
		<h2 id="credits-title">{m.admin_sys_credits_title()}</h2>
		<p class="muted small">{m.admin_sys_credits_intro()}</p>
		{#if attributionsQuery.data?.length}
			<ul class="credits">
				{#each attributionsQuery.data as attribution (attribution.name)}
					<li>
						<div class="credit-head">
							<strong>{attribution.name}</strong>
							<a
								href={attribution.licenseUrl}
								target="_blank"
								rel="noreferrer noopener"
								class="chip tag">{attribution.license}</a
							>
							{#if attribution.datasetDate}
								<span class="muted small"
									>{m.admin_sys_credits_dataset({ date: localDate(attribution.datasetDate) })}</span
								>
							{/if}
						</div>
						<p class="small">{attribution.notice}</p>
						<a class="small" href={attribution.sourceUrl} target="_blank" rel="noreferrer noopener"
							>{attribution.sourceUrl}</a
						>
					</li>
				{/each}
			</ul>
		{:else if attributionsQuery.isSuccess}
			<p class="muted">{m.admin_sys_credits_none()}</p>
		{/if}
	</section>
</AdminPage>

<style>
	h2 {
		margin: 0 0 var(--space-3);
		font-size: var(--font-size-md);
	}

	h3 {
		margin: var(--space-3) 0 var(--space-2);
		font-size: var(--font-size-sm);
	}

	p {
		margin: 0;
	}

	.columns {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: var(--space-3);
	}

	.card {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}

	.card > h2 {
		margin: 0;
	}

	.current {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		font-size: var(--font-size-xl);
	}

	.foot {
		margin-top: auto;
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
	}

	.facts {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--space-2) var(--space-4);
		margin: 0;
		font-size: var(--font-size-sm);
	}

	.facts dt {
		color: var(--color-text-muted);
	}

	.facts dd {
		margin: 0;
		overflow-wrap: anywhere;
	}

	.notes {
		max-height: 220px;
		overflow: auto;
		margin: 0;
		padding: var(--space-3);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font: inherit;
		font-size: var(--font-size-sm);
		white-space: pre-wrap;
	}

	code {
		font-size: 0.9em;
	}

	.credits {
		display: grid;
		gap: var(--space-3);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.credits li {
		display: grid;
		gap: var(--space-1);
		padding-top: var(--space-3);
		border-top: 1px solid var(--color-border);
	}

	.credit-head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	a:not(.btn):not(.chip) {
		color: var(--color-accent);
	}

	a.chip {
		text-decoration: none;
	}

	@media (max-width: 1000px) {
		.columns {
			grid-template-columns: minmax(0, 1fr);
		}
	}
</style>
