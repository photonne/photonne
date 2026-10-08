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
	import Icon from '#lib/components/Icon.svelte';
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

<div class="public">
	<header class="topbar">
		<a class="brand" href={appHref('/')}>{m.app_name()}</a>
		{#if session.status === 'signedIn'}
			<a class="library" href={appHref('/')}>{m.share_go_to_library()}</a>
		{/if}
	</header>

	<main id="content">
		{#if share.isPending}
			<p class="center" role="status">{m.session_restoring()}</p>
		{:else if !outcome || outcome.kind === 'unreachable'}
			<div class="center">
				<Icon path={ICON_BROKEN_IMAGE} size={48} />
				<h1>{m.session_unreachable_title()}</h1>
				<p>{m.session_unreachable_body()}</p>
				<button type="button" class="button" onclick={() => share.refetch()}>
					{m.session_retry()}
				</button>
			</div>
		{:else if outcome.kind === 'password'}
			<form class="gate" onsubmit={submitPassword}>
				<span class="gate-icon"><Icon name="lock" size={32} /></span>
				<h1>{m.share_password_title()}</h1>
				<p>{m.share_password_lead()}</p>
				<label>
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
				<button type="submit" class="button primary" disabled={!typed || share.isFetching}>
					{m.share_password_submit()}
				</button>
			</form>
		{:else if outcome.kind === 'expired'}
			<div class="center">
				<Icon path={ICON_SCHEDULE} size={48} />
				<h1>{m.share_expired_title()}</h1>
				<p>{m.share_expired_body()}</p>
			</div>
		{:else if outcome.kind === 'maxViews'}
			<div class="center">
				<Icon path={ICON_VISIBILITY_OFF} size={48} />
				<h1>{m.share_max_views_title()}</h1>
				<p>{m.share_max_views_body()}</p>
			</div>
		{:else if outcome.kind === 'notFound' || !content?.album}
			<div class="center">
				<Icon path={ICON_LINK_OFF} size={48} />
				<h1>{m.share_not_found_title()}</h1>
				<p>{m.share_not_found_body()}</p>
			</div>
		{:else}
			{@const album = content.album}
			{@const cover = coverOf(assets)}
			<section class="hero" aria-labelledby="album-title">
				{#if cover}<div class="hero-bg" style:background-image="url('{cover}')"></div>{/if}
				<div class="hero-content">
					<h1 id="album-title">{album.name}</h1>
					{#if album.description}<p class="description">{album.description}</p>{/if}
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
						<div class="hero-actions">
							<button type="button" class="button" onclick={downloadAll}>
								<Icon name="download" size={18} />
								{m.share_download_all()}
							</button>
						</div>
					{/if}
				</div>
			</section>

			<div class="body">
				{#if content.allowUpload}
					<ShareUploadCard {token} {password} onuploaded={() => share.refetch()} />
				{/if}

				{#if assets.length === 0}
					<p class="empty">{m.share_empty()}</p>
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
	</main>
</div>

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
		padding: 0 var(--space-6);
		border-bottom: 1px solid var(--color-border);
	}

	.brand {
		font-weight: 700;
		font-size: var(--font-size-lg);
		color: inherit;
		text-decoration: none;
	}

	.library {
		margin-left: auto;
		color: var(--color-accent);
		font-weight: 600;
	}

	main {
		flex: 1;
		display: flex;
		flex-direction: column;
	}

	.center,
	.gate {
		margin: auto;
		display: grid;
		justify-items: center;
		gap: var(--space-2);
		max-width: 420px;
		padding: var(--space-8) var(--space-6);
		text-align: center;
		color: var(--color-text-muted);
	}

	.center h1,
	.gate h1 {
		margin: var(--space-2) 0 0;
		font-size: var(--font-size-lg);
		color: var(--color-text);
	}

	.center p,
	.gate p {
		margin: 0;
	}

	.gate {
		width: min(100%, 400px);
		gap: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface-raised);
		box-shadow: var(--shadow-raised);
	}

	.gate-icon {
		display: grid;
		place-items: center;
		width: 64px;
		height: 64px;
		border-radius: 50%;
		background: var(--color-surface);
		color: var(--color-text);
	}

	.gate label {
		display: grid;
		gap: var(--space-1);
		width: 100%;
		text-align: left;
		font-size: var(--font-size-sm);
	}

	.gate .button {
		width: 100%;
		justify-content: center;
	}

	.error {
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}

	.button {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		color: var(--color-text);
		cursor: pointer;
	}

	.button.primary {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
	}

	.button:disabled {
		opacity: 0.6;
		cursor: default;
	}

	.hero {
		position: relative;
		overflow: hidden;
		min-height: 240px;
		display: flex;
		align-items: flex-end;
		background: #1d1d1f;
		color: #fff;
	}

	.hero-bg {
		position: absolute;
		inset: -24px;
		background-size: cover;
		background-position: center;
		filter: blur(18px) brightness(0.55);
	}

	.hero-content {
		position: relative;
		display: grid;
		gap: var(--space-2);
		width: 100%;
		max-width: 1400px;
		margin: 0 auto;
		padding: var(--space-8) var(--space-6) var(--space-6);
	}

	.hero h1 {
		margin: 0;
		font-size: 2.25rem;
		line-height: 1.15;
	}

	.description {
		margin: 0;
		max-width: 70ch;
		color: rgb(255 255 255 / 0.85);
	}

	.meta {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-4);
		margin: 0;
		color: rgb(255 255 255 / 0.75);
		font-size: var(--font-size-sm);
	}

	.meta span {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
	}

	.hero-actions {
		margin-top: var(--space-2);
	}

	.hero-actions .button {
		border-color: rgb(255 255 255 / 0.4);
		background: rgb(255 255 255 / 0.12);
		color: #fff;
	}

	.hero-actions .button:hover {
		background: rgb(255 255 255 / 0.2);
	}

	.body {
		display: grid;
		gap: var(--space-6);
		width: 100%;
		max-width: 1400px;
		margin: 0 auto;
		padding: var(--space-6);
	}

	.empty {
		color: var(--color-text-muted);
		text-align: center;
	}
</style>
