<script lang="ts">
	import { copyText } from '#lib/clipboard.js';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { icons } from '#lib/actions/icons.js';
	import { invalidateAlbums } from '#lib/albums/cache.js';
	import LinkForm from '#lib/albums/LinkForm.svelte';
	import {
		absoluteShareUrl,
		formFromLink,
		updateLinkRequest,
		type LinkForm as LinkFormModel
	} from '#lib/albums/share-link.js';
	import { revokeShareLink, updateShareLink, type SentShareLinkDto } from '#lib/api/index.js';
	import {
		getSentShareLinksOptions,
		getSentShareLinksQueryKey,
		listShareLinksQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import {
		filterLinks,
		linkBadges,
		linkCover,
		linkSubject,
		linkTotals,
		subjectPath,
		type LinkBadge
	} from './links-model.js';

	const queryClient = useQueryClient();
	const links = createQuery(() => getSentShareLinksOptions());

	let query = $state('');
	/** Token of the link being edited. */
	let editing = $state<string | null>(null);
	let form = $state<LinkFormModel>(formFromLink());
	let busy = $state(false);
	let revoking = $state<SentShareLinkDto | null>(null);

	const all = $derived(links.data ?? []);
	const shown = $derived(filterLinks(all, query));
	const totals = $derived(linkTotals(all));
	const dateFormat = new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium' });

	const badgeLabels: Record<LinkBadge, () => string> = {
		active: m.links_status_active,
		expired: m.albums_link_expired,
		exhausted: m.albums_link_used_up,
		password: m.albums_link_has_password,
		no_download: m.albums_link_no_download,
		upload: m.links_status_upload
	};

	function urlOf(link: SentShareLinkDto) {
		return absoluteShareUrl(link.shareUrl, location.origin);
	}

	function titleOf(link: SentShareLinkDto) {
		const subject = linkSubject(link);
		if (subject.kind === 'unknown') return m.links_unknown_subject();
		return subject.name || (subject.kind === 'album' ? m.links_kind_album() : m.links_kind_photo());
	}

	function details(link: SentShareLinkDto) {
		const parts = [
			m.links_created_on({ date: dateFormat.format(new Date(link.createdAt)) }),
			link.expiresAt
				? m.albums_link_until({ date: dateFormat.format(new Date(link.expiresAt)) })
				: m.albums_link_no_expiry()
		];
		if (link.allowUpload) parts.push(m.albums_link_uploads({ count: link.uploadCount }));
		return parts.join(' · ');
	}

	function views(link: SentShareLinkDto) {
		return link.maxViews !== null
			? m.albums_link_views_of({ count: link.viewCount, max: link.maxViews })
			: m.albums_link_views({ count: link.viewCount });
	}

	async function copy(link: SentShareLinkDto) {
		try {
			await copyText(urlOf(link));
			toasts.show(m.albums_link_copied());
		} catch {
			toasts.error(m.albums_link_copy_failed());
		}
	}

	function edit(link: SentShareLinkDto) {
		form = formFromLink(link);
		editing = link.token;
	}

	/** This page, the album's own links panel and the albums' "shared" mark. */
	async function refresh(link: SentShareLinkDto) {
		if (link.albumId) {
			invalidateAlbums(queryClient, link.albumId);
			queryClient.invalidateQueries({
				queryKey: listShareLinksQueryKey({ query: { albumId: link.albumId } })
			});
		}
		await queryClient.invalidateQueries({ queryKey: getSentShareLinksQueryKey() });
	}

	async function save(link: SentShareLinkDto) {
		busy = true;
		const { error } = await updateShareLink({
			path: { token: link.token },
			body: updateLinkRequest(form)
		});
		busy = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		editing = null;
		await refresh(link);
		toasts.show(m.albums_link_updated());
	}

	async function revoke() {
		const link = revoking;
		if (!link) return;
		busy = true;
		const { error } = await revokeShareLink({ path: { token: link.token } });
		busy = false;
		revoking = null;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		await refresh(link);
		toasts.show(m.albums_link_revoked());
	}
</script>

<svelte:head>
	<title>{m.links_title()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader
		title={m.links_title()}
		count={links.isSuccess && all.length > 0
			? `${m.links_totals_active({ count: totals.active })} · ${m.albums_link_views({ count: totals.views })}`
			: null}
		subtitle={m.links_intro()}
	>
		{#snippet toolbar()}
			{#if all.length > 0}
				<label class="search">
					<Icon name="search" size={18} />
					<span class="visually-hidden">{m.links_search()}</span>
					<input type="search" placeholder={m.links_search()} bind:value={query} />
				</label>
			{/if}
		{/snippet}
	</PageHeader>

	{#if links.isPending}
		<Skeleton variant="rows" count={4} />
	{:else if links.isError}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if all.length === 0}
		<EmptyState icon="link" title={m.links_empty_title()} hint={m.links_empty_body()}>
			{#snippet action()}
				<a class="btn primary" href={appHref('/albums')}>
					<Icon name="add" size={18} />{m.links_empty_action()}
				</a>
			{/snippet}
		</EmptyState>
	{:else if shown.length === 0}
		<EmptyState compact icon="search" title={m.links_no_match()}>
			{#snippet action()}
				<button type="button" class="btn" onclick={() => (query = '')}>
					{m.albums_clear_filters()}
				</button>
			{/snippet}
		</EmptyState>
	{:else}
		<ul class="list" aria-label={m.links_title()}>
			{#each shown as link (link.token)}
				{@const subject = linkSubject(link)}
				{@const path = subjectPath(subject)}
				{@const cover = linkCover(link)}
				{@const title = titleOf(link)}
				<li class="row" aria-label={title}>
					<div class="cover">
						{#if cover}
							<img src={cover} alt="" loading="lazy" decoding="async" />
						{:else}
							<Icon name={subject.kind === 'asset' ? 'photos' : 'album'} size={28} />
						{/if}
					</div>

					<div class="body">
						<div class="title-row">
							<h2>
								{#if path}<a href={appHref(path)}>{title}</a>{:else}{title}{/if}
							</h2>
							<span class="kind">
								{subject.kind === 'asset' ? m.links_kind_photo() : m.links_kind_album()}
							</span>
						</div>

						<ul class="badges" aria-label={m.links_status()}>
							{#each linkBadges(link) as badge (badge)}
								<li class="badge {badge}">
									{#if badge === 'password'}<Icon name="lock" size={12} />{/if}
									{badgeLabels[badge]()}
								</li>
							{/each}
							<li class="badge views">
								<Icon path={icons.views} size={12} />{views(link)}
							</li>
						</ul>
						<p class="details">{details(link)}</p>

						{#if editing === link.token}
							<LinkForm
								bind:form
								hadPassword={link.hasPassword}
								submitLabel={m.albums_save()}
								{busy}
								onsubmit={() => save(link)}
								oncancel={() => (editing = null)}
							/>
						{:else}
							<input
								class="url"
								readonly
								value={urlOf(link)}
								aria-label={m.albums_link_url()}
								onfocus={(event) => event.currentTarget.select()}
							/>
							<div class="buttons">
								<button type="button" class="btn sm" onclick={() => copy(link)}>
									<Icon name="copy" size={16} />{m.albums_link_copy()}
								</button>
								<a class="btn sm" href={urlOf(link)} target="_blank" rel="noopener noreferrer">
									<Icon path={icons.openInNew} size={16} />{m.links_open()}
								</a>
								<button type="button" class="btn sm" onclick={() => edit(link)}>
									<Icon name="edit" size={16} />{m.albums_link_edit()}
								</button>
								<button type="button" class="btn sm ghost revoke" onclick={() => (revoking = link)}>
									<Icon name="delete" size={16} />{m.albums_link_revoke()}
								</button>
							</div>
						{/if}
					</div>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<ConfirmDialog
	open={revoking !== null}
	title={m.albums_link_revoke_title()}
	message={m.albums_link_revoke_message()}
	confirmLabel={m.albums_link_revoke()}
	danger
	{busy}
	onconfirm={revoke}
	onclose={() => (revoking = null)}
/>

<style>
	.page {
		display: grid;
		align-content: start;
		max-width: calc(960px + 2 * var(--page-gutter));
		margin-inline: auto;
		padding-bottom: var(--space-8);
	}

	.search {
		position: relative;
		display: flex;
		align-items: center;
		color: var(--color-text-muted);
	}

	.search :global(svg) {
		position: absolute;
		left: var(--space-3);
		pointer-events: none;
	}

	.search input {
		width: 240px;
		padding-left: calc(var(--space-3) + 26px);
	}

	.status {
		margin: 0;
		padding: var(--space-6) var(--page-gutter);
		color: var(--color-text-muted);
	}

	.list {
		display: grid;
		gap: var(--space-3);
		margin: 0;
		padding: var(--space-2) var(--page-gutter) 0;
		list-style: none;
	}

	.row {
		display: grid;
		grid-template-columns: 96px minmax(0, 1fr);
		gap: var(--space-4);
		padding: var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
	}

	.cover {
		display: grid;
		place-items: center;
		width: 96px;
		height: 96px;
		overflow: hidden;
		border-radius: var(--radius-md);
		background: var(--color-brand-tile);
		color: var(--color-brand);
	}

	.cover img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.body {
		display: grid;
		gap: var(--space-2);
		min-width: 0;
	}

	.title-row {
		display: flex;
		align-items: baseline;
		gap: var(--space-2);
		min-width: 0;
	}

	h2 {
		margin: 0;
		overflow: hidden;
		font-size: var(--font-size-md);
		font-weight: 600;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	h2 a {
		color: inherit;
		text-decoration: none;
	}

	h2 a:hover,
	h2 a:focus-visible {
		text-decoration: underline;
	}

	.kind {
		flex: none;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.badges {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	/* Status pills: tinted by meaning, the text in the full colour (AA). */
	.badge {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: 24px;
		padding: 0 var(--space-2);
		border-radius: 999px;
		background: var(--color-surface);
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
		font-weight: 600;
		white-space: nowrap;
	}

	.badge.active {
		background: color-mix(in srgb, var(--color-success) 14%, transparent);
		color: var(--color-success);
	}

	.badge.upload {
		background: var(--color-accent-soft);
		color: var(--color-text);
	}

	.badge.expired,
	.badge.exhausted {
		background: color-mix(in srgb, var(--color-danger) 14%, transparent);
		color: var(--color-danger);
	}

	.details {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.url {
		width: 100%;
		min-height: var(--control-h-sm);
		background: var(--color-surface);
		font-family: ui-monospace, monospace;
		font-size: var(--font-size-xs);
	}

	.buttons {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.revoke {
		color: var(--color-danger);
	}

	@media (max-width: 560px) {
		.row {
			grid-template-columns: 64px minmax(0, 1fr);
		}

		.cover {
			width: 64px;
			height: 64px;
		}
	}
</style>
