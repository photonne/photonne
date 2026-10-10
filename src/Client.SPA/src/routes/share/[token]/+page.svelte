<script lang="ts">
	import { tick } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { page } from '$app/state';
	import {
		ICON_BROKEN_IMAGE,
		ICON_LINK_OFF,
		ICON_SCHEDULE,
		ICON_VISIBILITY_OFF
	} from '#lib/account/icons.js';
	import PasswordInput from '#lib/account/settings/PasswordInput.svelte';
	import {
		openShare,
		rememberedPassword,
		rememberPassword,
		shareMediaUrl
	} from '#lib/account/share/share-api.js';
	import ShareGrid from '#lib/account/share/ShareGrid.svelte';
	import ShareUploadCard from '#lib/account/share/ShareUploadCard.svelte';
	import ShareViewer from '#lib/account/share/ShareViewer.svelte';
	import type { SharedAssetDto } from '#lib/api/index.js';
	import { session } from '#lib/auth/session.svelte.js';
	import AppShell from '#lib/components/AppShell.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import Logo from '#lib/components/Logo.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { longDate } from '#lib/format.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';

	const token = $derived(page.params.token ?? '');

	// The password that opened the link (null: none needed or not given yet).
	let password = $state<string | null>(rememberedPassword(page.params.token ?? ''));
	let typed = $state('');

	// Every open counts as a view on the server: fetched once per password,
	// never refetched in the background.
	const share = createQuery(() => ({
		queryKey: ['share', token, password],
		queryFn: () => openShare(token, password ?? undefined),
		staleTime: Infinity,
		retry: false,
		refetchOnWindowFocus: false,
		refetchOnReconnect: false
	}));

	const outcome = $derived(share.data);
	const content = $derived(outcome?.kind === 'content' ? outcome.content : null);
	const assets = $derived(content?.assets ?? []);

	$effect(() => {
		if (content) rememberPassword(token, password);
		else if (outcome?.kind === 'password' && outcome.wrong) rememberPassword(token, null);
	});

	const viewer = new ViewerRoute();
	let grid = $state<ShareGrid>();

	async function closeViewer() {
		const last = await viewer.close();
		await tick();
		if (last) grid?.focusItem(last);
	}

	function submitPassword(event: SubmitEvent) {
		event.preventDefault();
		if (!typed) return;
		if (typed === password) share.refetch();
		else password = typed;
	}

	async function downloadAll() {
		toasts.show(m.share_downloading({ count: assets.length }));
		// One file per click; browsers ask once to allow several downloads.
		for (const asset of assets) {
			const link = document.createElement('a');
			link.href = shareMediaUrl(token, asset.id, { content: true, download: true }, password);
			link.download = asset.fileName;
			document.body.append(link);
			link.click();
			link.remove();
			await new Promise((resolve) => setTimeout(resolve, 400));
		}
	}

	function coverOf(list: readonly SharedAssetDto[]) {
		return list[0] ? shareMediaUrl(token, list[0].id, { thumbnail: 'Large' }, password) : null;
	}

	const title = $derived(content?.album?.name ?? m.share_title());
</script>

<svelte:head>
	<title>{title} · {m.app_name()}</title>
	<meta name="robots" content="noindex" />
</svelte:head>

{#snippet notice(iconPath: string, heading: string, text: string)}
	<div class="center">
		<span class="mark" aria-hidden="true"><Icon path={iconPath} size={32} /></span>
		<h1>{heading}</h1>
		<p>{text}</p>
	</div>
{/snippet}

{#snippet body()}
	{#if share.isPending}
		<Skeleton variant="grid" />
	{:else if !outcome || outcome.kind === 'unreachable'}
		<div class="center">
			<span class="mark" aria-hidden="true"><Icon path={ICON_BROKEN_IMAGE} size={32} /></span>
			<h1>{m.session_unreachable_title()}</h1>
			<p>{m.session_unreachable_body()}</p>
			<button type="button" class="btn" onclick={() => share.refetch()}>
				{m.session_retry()}
			</button>
		</div>
	{:else if outcome.kind === 'password'}
		<form class="gate" onsubmit={submitPassword}>
			<span class="mark" aria-hidden="true"><Icon name="lock" size={28} /></span>
			<h1>{m.share_password_title()}</h1>
			<p>{m.share_password_lead()}</p>
			<label class="field">
				<span>{m.share_password_label()}</span>
				<PasswordInput
					autocomplete="off"
					required
					autofocus
					aria-invalid={outcome.wrong}
					aria-describedby={outcome.wrong ? 'password-error' : undefined}
					bind:value={typed}
				/>
			</label>
			{#if outcome.wrong}
				<p id="password-error" class="error" role="alert">{m.share_password_wrong()}</p>
			{/if}
			<button type="submit" class="btn primary lg" disabled={!typed || share.isFetching}>
				{m.share_password_submit()}
			</button>
		</form>
	{:else if outcome.kind === 'expired'}
		{@render notice(ICON_SCHEDULE, m.share_expired_title(), m.share_expired_body())}
	{:else if outcome.kind === 'maxViews'}
		{@render notice(ICON_VISIBILITY_OFF, m.share_max_views_title(), m.share_max_views_body())}
	{:else if outcome.kind === 'notFound' || !content?.album}
		{@render notice(ICON_LINK_OFF, m.share_not_found_title(), m.share_not_found_body())}
	{:else}
		{@const album = content.album}
		{@const cover = coverOf(assets)}
		<!-- As an album's banner in the app: the cover sharp, a gradient, the title on it. -->
		<section class="hero" class:plain={!cover} aria-labelledby="album-title">
			{#if cover}<img class="hero-image" src={cover} alt="" decoding="async" />{/if}
			<div class="hero-content">
				<h1 id="album-title">{album.name}</h1>
				{#if album.description}<p class="description">{album.description}</p>{/if}
				<div class="hero-row">
					<p class="meta">
						<span><Icon name="photos" size={16} /> {m.share_items({ count: assets.length })}</span>
						{#if content.expiresAt}
							<span>
								<Icon path={ICON_SCHEDULE} size={16} />
								{m.share_expires({ date: longDate(content.expiresAt) })}
							</span>
						{/if}
					</p>
					{#if content.allowDownload && assets.length > 0}
						<button type="button" class="btn on-photo" onclick={downloadAll}>
							<Icon name="download" size={18} />
							{m.share_download_all()}
						</button>
					{/if}
				</div>
			</div>
		</section>

		<div class="body">
			{#if content.allowUpload}
				<ShareUploadCard {token} {password} onuploaded={() => share.refetch()} />
			{/if}

			{#if assets.length === 0}
				<EmptyState compact icon="photos" title={m.share_empty()} />
			{:else}
				<ShareGrid
					bind:this={grid}
					{token}
					{password}
					{assets}
					label={album.name ?? m.share_title()}
					onopen={(asset) => viewer.open(asset.id)}
				/>
			{/if}
		</div>

		{#if viewer.openId && assets.some((asset) => asset.id === viewer.openId)}
			<ShareViewer
				{token}
				{password}
				{assets}
				assetId={viewer.openId}
				allowDownload={content.allowDownload}
				onnavigate={(id) => viewer.navigate(id)}
				onclose={closeViewer}
			/>
		{/if}
	{/if}
{/snippet}

{#if session.status === 'signedIn'}
	<!-- A Photonne user opening a link sees it inside the app, with their library at hand. -->
	<AppShell>
		<div class="in-app">{@render body()}</div>
	</AppShell>
{:else if session.status === 'restoring'}
	<Skeleton variant="grid" />
{:else}
	<div class="public">
		<header class="topbar">
			<a class="brand" href={appHref('/')} aria-label={m.app_name()}><Logo size={30} /></a>
			<a
				class="btn sm library"
				href="{appHref('/login')}?returnTo={encodeURIComponent(page.url.pathname)}"
				>{m.share_sign_in()}</a
			>
		</header>
		<main id="content">{@render body()}</main>
	</div>
{/if}

<style>
	.public {
		min-height: 100vh;
		display: flex;
		flex-direction: column;
	}

	.topbar {
		display: flex;
		align-items: center;
		gap: var(--space-4);
		height: var(--header-height);
		padding: 0 var(--page-gutter);
		border-bottom: 1px solid var(--color-border);
	}

	.brand {
		display: flex;
		color: inherit;
		text-decoration: none;
	}

	.library {
		margin-left: auto;
	}

	.in-app {
		display: flex;
		flex-direction: column;
		min-height: 100%;
	}

	main {
		flex: 1;
		display: flex;
		flex-direction: column;
	}

	/* --- Notices: a link that no longer works, a password ----------------- */

	.center,
	.gate {
		margin: auto;
		display: grid;
		justify-items: center;
		gap: var(--space-2);
		max-width: 440px;
		padding: var(--space-8) var(--space-6);
		text-align: center;
		color: var(--color-text-muted);
	}

	/* As EmptyState's mark (components/ui/EmptyState.svelte). */
	.mark {
		display: grid;
		place-items: center;
		width: 72px;
		height: 72px;
		margin-bottom: var(--space-2);
		border-radius: 50%;
		background: var(--color-brand-tile);
		color: var(--color-brand);
	}

	.center h1,
	.gate h1 {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
		color: var(--color-text);
	}

	.center p,
	.gate p {
		margin: 0;
		font-size: var(--font-size-sm);
	}

	.center .btn {
		margin-top: var(--space-3);
	}

	.gate {
		width: min(100% - 2 * var(--space-4), 400px);
		gap: var(--space-3);
		padding: var(--space-8) var(--space-6) var(--space-6);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
	}

	.gate .mark {
		width: 60px;
		height: 60px;
		margin-bottom: 0;
	}

	.gate .field {
		width: 100%;
		margin-top: var(--space-2);
		text-align: left;
	}

	.gate .btn {
		width: 100%;
	}

	.error {
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}

	/* --- The album: banner, upload card, grid ------------------------------ */

	.hero {
		position: relative;
		display: flex;
		align-items: flex-end;
		width: calc(100% - 2 * var(--page-gutter));
		max-width: 1400px;
		height: clamp(220px, 34vh, 360px);
		margin: var(--space-4) auto 0;
		overflow: hidden;
		border-radius: var(--radius-lg);
		background: #1d1d1f;
		color: #fff;
	}

	.hero-image {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.hero::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(to top, rgb(0 0 0 / 0.75), rgb(0 0 0 / 0.15) 60%, transparent);
	}

	.hero-content {
		position: relative;
		z-index: 1;
		display: grid;
		gap: var(--space-1);
		width: 100%;
		padding: var(--space-6);
	}

	.hero h1 {
		margin: 0;
		font-size: var(--font-size-2xl);
		font-weight: 650;
		line-height: 1.15;
		letter-spacing: -0.01em;
		text-shadow: 0 1px 8px rgb(0 0 0 / 0.4);
	}

	.description {
		margin: 0;
		max-width: 70ch;
		color: rgb(255 255 255 / 0.9);
		white-space: pre-line;
	}

	.hero-row {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
		margin-top: var(--space-1);
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-4);
		margin: 0;
		color: rgb(255 255 255 / 0.85);
		font-size: var(--font-size-sm);
	}

	.meta span {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
	}

	/* A button on the photo: white glass, legible on any cover. */
	.btn.on-photo {
		border-color: rgb(255 255 255 / 0.5);
		background: rgb(255 255 255 / 0.14);
		color: #fff;
		backdrop-filter: blur(6px);
	}

	.btn.on-photo:hover {
		background: rgb(255 255 255 / 0.24);
	}

	/* No cover photo: the same banner on the brand tile, in the page's colours. */
	.hero.plain {
		height: auto;
		min-height: 160px;
		background: var(--color-brand-tile);
		color: var(--color-text);
	}

	.hero.plain::after {
		display: none;
	}

	.plain h1 {
		text-shadow: none;
	}

	.plain .description,
	.plain .meta {
		color: var(--color-text-muted);
	}

	.plain .btn.on-photo {
		border-color: var(--color-border-strong);
		background: var(--color-surface-raised);
		color: var(--color-text);
		backdrop-filter: none;
	}

	.body {
		display: grid;
		gap: var(--space-6);
		width: 100%;
		max-width: calc(1400px + 2 * var(--page-gutter));
		margin: 0 auto;
		padding: var(--space-6) var(--page-gutter);
	}
</style>
