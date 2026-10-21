<script lang="ts">
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import PopupMenu, { type MenuEntry } from '#lib/components/ui/PopupMenu.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import type { Snippet } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { afterNavigate, goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { dropOnAlbum, dropOnFolder, isAssetDrag } from '#lib/actions/drag-assets.js';
	import {
		getAllAlbumsOptions,
		getAllFoldersOptions,
		getUnreadNotificationsCountOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import DropZone from '#lib/account/upload/DropZone.svelte';
	import { isSmart } from '#lib/albums/album-list.js';
	import { icons } from '#lib/albums/icons.js';
	import { mergePinned } from '#lib/albums/pinned.js';
	import { uploads } from '#lib/account/upload/uploads.svelte.js';
	import { session } from '#lib/auth/session.svelte.js';
	import { appHref as href } from '#lib/navigation/href.js';
	import { navigation } from '#lib/navigation/sections.js';
	import {
		initialCollapsed,
		RAIL_QUERY,
		saveCollapsed,
		savedCollapsed
	} from '#lib/navigation/sidebar.js';
	import { m } from '#lib/paraglide/messages.js';
	import { chrome } from '#lib/navigation/chrome.svelte.js';
	import CommandPalette, { type PaletteEntry } from './CommandPalette.svelte';
	import FloatingNav from './FloatingNav.svelte';
	import Icon from './Icon.svelte';
	import Logo from './Logo.svelte';

	let { children }: { children: Snippet } = $props();

	const fullName = $derived(
		[session.user?.firstName, session.user?.lastName].filter(Boolean).join(' ')
	);

	const albums = createQuery(() => getAllAlbumsOptions());
	const folders = createQuery(() => getAllFoldersOptions());
	// Polled lightly so new notifications show up without a reload.
	const unread = createQuery(() => ({
		...getUnreadNotificationsCountOptions(),
		refetchInterval: 60_000
	}));
	const unreadCount = $derived(unread.data?.count ?? 0);

	const pinned = $derived(
		mergePinned(albums.data ?? [], folders.data ?? []).map((entry) => ({
			kind: entry.kind,
			id: entry.item.id,
			name: entry.item.name,
			// Smart albums fill themselves: they open, but photos can't be dropped on them.
			smart: entry.kind === 'album' && isSmart(entry.item)
		}))
	);

	// Everywhere the palette (Ctrl/Cmd + K) can go: the menu's sections, the
	// pinned albums and folders, and the account's pages.
	const paletteEntries: PaletteEntry[] = $derived([
		...navigation
			.filter((section) => !section.adminOnly || session.isAdmin)
			.flatMap((section) =>
				section.items.map((item) => ({
					href: href(item.path),
					label: item.label(),
					group: section.label?.(),
					icon: item.icon
				}))
			),
		...pinned.map((item) => ({
			href: href(item.kind === 'album' ? `/albums/${item.id}` : `/folders/${item.id}`),
			label: item.name,
			group: m.nav_section_pinned(),
			...(item.smart
				? { iconPath: icons.smart }
				: { icon: item.kind === 'album' ? ('album' as const) : ('folder' as const) })
		})),
		{
			href: href('/notifications'),
			label: m.nav_notifications(),
			group: m.nav_section_account(),
			icon: 'notifications'
		},
		{
			href: href('/settings'),
			label: m.nav_settings(),
			group: m.nav_section_account(),
			icon: 'settings'
		}
	]);
	let palette = $state<CommandPalette>();
	const paletteKeys = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘K' : 'Ctrl K';

	let dropTarget = $state<string | null>(null);
	// A rail of icons or the full menu; narrow windows start as a rail.
	let collapsed = $state(initialCollapsed(window.innerWidth));

	// Until the user picks one, the menu follows the window: a rail when it
	// narrows, the full menu when it widens again.
	let chosen = savedCollapsed() !== null;
	$effect(() => {
		const query = window.matchMedia(RAIL_QUERY);
		const follow = () => {
			if (!chosen) collapsed = query.matches;
		};
		query.addEventListener('change', follow);
		return () => query.removeEventListener('change', follow);
	});

	// On a phone-sized window the island gives way to the native app's
	// navigation: three tabs in a capsule at the bottom (FloatingNav).
	const PHONE_QUERY = '(max-width: 700px)';
	let mobile = $state(window.matchMedia(PHONE_QUERY).matches);
	/** Icons only: the rail. */
	const rail = $derived(collapsed && !mobile);

	$effect(() => {
		const query = window.matchMedia(PHONE_QUERY);
		const follow = () => {
			mobile = query.matches;
			if (!mobile) resetImmersive();
		};
		query.addEventListener('change', follow);
		return () => query.removeEventListener('change', follow);
	});

	function toggleSidebar() {
		collapsed = !collapsed;
		chosen = true;
		saveCollapsed(collapsed);
	}

	const menuLabel = $derived(collapsed ? m.nav_expand() : m.nav_collapse());

	// Immersive on a phone, as the native ImmersiveChromeEffect: scrolling
	// down any pane of the page hides the chrome; scrolling up, reaching the
	// top or stopping for a moment brings it back. A few pixels of slack
	// ignore jitter.
	const SCROLL_SLACK = 10;
	const SETTLE_MS = 600;
	const lastTops = new WeakMap<Element, number>();
	let content = $state<HTMLElement>();
	let settle: ReturnType<typeof setTimeout> | undefined;

	function onscroll(event: Event) {
		const pane = event.target;
		if (!mobile || !(pane instanceof Element)) return;
		const top = pane.scrollTop;
		const last = lastTops.get(pane) ?? 0;
		// The page itself scrolls under the chrome already; a grid with its
		// own scroll needs the room around the page given back.
		// (A full-window page keeps that room inside its panes already.)
		const inner = pane !== content && !chrome.bleed;
		clearTimeout(settle);
		if (top <= SCROLL_SLACK) {
			chrome.immersive = false;
			if (inner) chrome.reclaimed = false;
		} else if (top - last > SCROLL_SLACK) {
			chrome.immersive = true;
			if (inner) chrome.reclaimed = true;
		} else if (last - top > SCROLL_SLACK) chrome.immersive = false;
		else return;
		lastTops.set(pane, top);
		if (chrome.immersive) settle = setTimeout(() => (chrome.immersive = false), SETTLE_MS);
	}

	function resetImmersive() {
		clearTimeout(settle);
		chrome.immersive = false;
		chrome.reclaimed = false;
	}

	afterNavigate(resetImmersive);
	$effect(() => () => clearTimeout(settle));
	let search = $state('');

	/**
	 * aria-current for a link: "page" when it is the page on screen, "true"
	 * when the page is inside its section (Álbumes on /albums/123), so only
	 * one link claims to be the page itself.
	 */
	function current(path: string): 'page' | 'true' | undefined {
		const target = href(path);
		const here = page.url.pathname;
		if (here === target) return 'page';
		if (path !== '/' && here.startsWith(`${target.replace(/\/$/, '')}/`)) return 'true';
		return undefined;
	}

	function addUploads(files: File[]) {
		const { ignored } = uploads.add(files);
		if (ignored > 0) toasts.show(m.upload_ignored({ count: ignored }));
	}

	let confirmingLogout = $state(false);

	const accountItems: MenuEntry[] = $derived([
		{ kind: 'link', label: m.nav_settings(), icon: 'settings', href: href('/settings') },
		{ label: m.account_logout(), icon: 'logout', run: requestLogout }
	]);

	function requestLogout() {
		if (uploads.active) confirmingLogout = true;
		else logout();
	}

	async function logout() {
		confirmingLogout = false;
		await session.logout();
		await goto(resolve('/login'), { replace: true });
	}

	function submitSearch(event: SubmitEvent) {
		event.preventDefault();
		const query = search.trim();
		if (!query) return;
		// The search page takes the words over: nothing stale left here for later.
		search = '';
		goto(`${href('/search')}?q=${encodeURIComponent(query)}`);
	}

	// The search page has its own, fuller box: one is enough.
	const onSearchPage = $derived(page.url.pathname === href('/search'));
</script>

<a class="skip-link" href="#content">{m.skip_to_content()}</a>

<!-- Files dropped anywhere in the app go to the upload queue. -->
<DropZone mode="window" onfiles={addUploads} />

<ConfirmDialog
	open={confirmingLogout}
	title={m.account_logout_uploads_title()}
	message={m.account_logout_uploads_body()}
	confirmLabel={m.account_logout()}
	danger
	onconfirm={logout}
	onclose={() => (confirmingLogout = false)}
/>

<div
	class="shell"
	class:collapsed={rail}
	class:immersive={chrome.immersive}
	class:reclaimed={chrome.reclaimed && mobile}
	class:selecting={chrome.selecting > 0}
	class:bleed={chrome.bleed}
	class:wide={chrome.wide}
>
	<!-- The navigation island: a glass panel floating beside the page, with
	     the brand and the fold button on top. Not on a phone: FloatingNav. -->
	<div class="island glass">
		<div class="brand-area">
			<button
				type="button"
				class="icon-btn"
				aria-controls="sidebar"
				aria-expanded={!collapsed}
				aria-label={menuLabel}
				title={menuLabel}
				onclick={toggleSidebar}
			>
				<Icon name="menu" />
			</button>
			<a class="brand" href={href('/')} aria-label={m.app_name()}
				><Logo size={28} markOnly={rail} /></a
			>
		</div>

		<nav id="sidebar" class="sidebar" aria-label={m.nav_main()}>
			{#each navigation.filter((section) => !section.adminOnly || session.isAdmin) as section (section.id)}
				{#if section.label}
					<h2 class:visually-hidden={rail}>{section.label()}</h2>
				{/if}
				<ul>
					{#each section.items as item (item.path)}
						<li>
							<a
								href={href(item.path)}
								aria-current={current(item.path)}
								title={rail ? item.label() : undefined}
							>
								<Icon name={item.icon} size={20} />
								<span class="label">{item.label()}</span>
							</a>
						</li>
					{/each}
				</ul>
				{#if section.id === 'collections'}
					{#if pinned.length > 0}
						<h2 class:visually-hidden={rail}>{m.nav_section_pinned()}</h2>
						<ul>
							{#each pinned as item (item.kind + item.id)}
								{@const path = item.kind === 'album' ? `/albums/${item.id}` : `/folders/${item.id}`}
								<li>
									<a
										href={href(path)}
										class:drop={dropTarget === item.id}
										aria-current={current(path)}
										title={item.smart
											? item.name
											: item.kind === 'album'
												? m.drop_add_to_album({ album: item.name })
												: m.drop_move_to_folder({ folder: item.name })}
										ondragover={(event) => {
											if (item.smart || !isAssetDrag(event)) return;
											event.preventDefault();
											dropTarget = item.id;
										}}
										ondragleave={() => (dropTarget = null)}
										ondrop={(event) => {
											if (item.smart) return;
											event.preventDefault();
											dropTarget = null;
											if (item.kind === 'album') dropOnAlbum(event, item);
											else dropOnFolder(event, item);
										}}
									>
										<Icon
											path={item.smart ? icons.smart : undefined}
											name={item.smart ? undefined : item.kind === 'album' ? 'album' : 'folder'}
											size={20}
										/>
										<span class="label">{item.name}</span>
									</a>
								</li>
							{/each}
						</ul>
					{/if}
				{/if}
			{/each}
		</nav>
	</div>

	<div class="stage" onscrollcapture={onscroll}>
		<!-- No bar: the search and the tools float over the page in two glass
		     capsules, and the page scrolls beneath them. On a phone the logo
		     takes the search's place and the search is a button. -->
		<header class="topbar">
			{#if mobile}
				<a class="brand-pill glass" href={href('/')} aria-label={m.app_name()}
					><Logo size={26} markOnly /></a
				>
			{:else if !onSearchPage}
				<form class="search glass" role="search" onsubmit={submitSearch}>
					<Icon name="search" size={20} />
					<input
						type="search"
						class="bare"
						aria-label={m.nav_search()}
						placeholder={m.nav_search()}
						bind:value={search}
					/>
					<button
						type="button"
						class="palette-hint"
						title={m.palette_open()}
						aria-label={m.palette_open()}
						aria-keyshortcuts="Control+K Meta+K"
						onclick={() => palette?.open()}>{paletteKeys}</button
					>
				</form>
			{/if}

			{#if chrome.actions}
				<!-- The page's own buttons (filters, view options…), between the
				     search and the user's tools. -->
				<div class="page-actions glass" role="group" aria-label={m.nav_page_tools()}>
					{@render chrome.actions()}
				</div>
			{/if}

			<div class="tools glass">
				{#if mobile && !onSearchPage}
					<a class="icon" href={href('/search')} aria-label={m.nav_search()}>
						<Icon name="search" />
					</a>
				{/if}
				{#if uploads.active}
					<!-- Uploads keep going from any page: how far along, one click from the queue. -->
					<a
						class="uploading"
						href={href('/upload')}
						title={m.upload_indicator_title({ percent: Math.round(uploads.progress * 100) })}
					>
						<Icon name="upload" size={18} />
						<span class="visually-hidden">{m.upload_indicator()}</span>
						<span class="upload-count"
							>{uploads.counts.total - uploads.counts.pending}/{uploads.counts.total}</span
						>
						<span class="upload-bar" aria-hidden="true"
							><span style:width="{uploads.progress * 100}%"></span></span
						>
					</a>
				{/if}
				<a
					class="icon bell"
					href={href('/notifications')}
					aria-label={unreadCount
						? m.nav_notifications_unread({ count: unreadCount })
						: m.nav_notifications()}
				>
					<Icon name="notifications" />
					{#if unreadCount}<span class="badge" aria-hidden="true"
							>{unreadCount > 99 ? '99+' : unreadCount}</span
						>{/if}
				</a>
				<PopupMenu
					label={m.account_menu_label({ name: session.user?.username ?? '' })}
					items={accountItems}
					triggerClass="account"
				>
					{#snippet trigger()}
						<span class="avatar" aria-hidden="true"
							>{(session.user?.firstName || session.user?.username || '?').charAt(0)}</span
						>
					{/snippet}
					{#snippet header()}
						<div class="identity">
							<strong>{fullName || session.user?.username}</strong>
							<span>{session.user?.email}</span>
						</div>
					{/snippet}
				</PopupMenu>
			</div>
		</header>

		<main id="content" class="content" tabindex="-1" bind:this={content}>
			{@render children()}
		</main>
	</div>

	<FloatingNav unread={unreadCount} />
	<CommandPalette bind:this={palette} entries={paletteEntries} />
</div>

<style>
	.shell {
		--nav-width: var(--sidebar-width);
		/* Where the page starts: floating bars centre themselves on it. */
		--stage-left: calc(var(--nav-width) + var(--float-margin));
		/* Height of the floating chrome above the page: the capsules and the
		   room around them. Pages scroll under it and start below it. */
		--chrome-top: calc(var(--float-margin) * 2 + var(--chrome-h));
		display: grid;
		grid-template-columns: calc(var(--nav-width) + var(--float-margin)) minmax(0, 1fr);
		height: 100dvh;
		transition: grid-template-columns var(--duration-chrome) var(--ease-standard);
	}

	.shell.collapsed {
		--nav-width: 64px;
	}

	.skip-link {
		position: absolute;
		left: var(--space-2);
		top: -100px;
		z-index: 10;
		padding: var(--space-2) var(--space-3);
		background: var(--color-surface-raised);
		border-radius: var(--radius-pill);
	}

	.skip-link:focus {
		top: var(--space-2);
	}

	/* ── The navigation island ── */

	/* On the plain page, not on photos: more frost, so it stands apart from
	   the background in both themes. As tall as its items, no taller: the
	   page shows below it; a long list scrolls inside, within the window. */
	.island {
		--glass-tint: 90%;
		position: relative;
		z-index: 6;
		align-self: start;
		display: flex;
		flex-direction: column;
		min-height: 0;
		max-height: calc(100dvh - 2 * var(--float-margin));
		margin: var(--float-margin) 0 var(--float-margin) var(--float-margin);
		border-radius: var(--radius-xl);
		box-shadow: var(--shadow-nav);
		overflow: hidden;
	}

	.brand-area {
		display: flex;
		align-items: center;
		gap: var(--space-1);
		flex: none;
		min-width: 0;
		padding: var(--space-3) var(--space-3) var(--space-2) var(--space-2);
	}

	.collapsed .brand-area {
		flex-direction: column;
		gap: var(--space-2);
		padding: var(--space-3) 0 var(--space-2);
	}

	.brand {
		display: flex;
		align-items: center;
		min-width: 0;
		border-radius: var(--radius-pill);
		color: inherit;
		text-decoration: none;
	}

	.sidebar {
		flex: 0 1 auto;
		min-height: 0;
		overflow-x: hidden;
		overflow-y: auto;
		padding: 0 var(--space-2) var(--space-4);
		scrollbar-width: none;
	}

	.sidebar h2 {
		margin: var(--space-4) var(--space-3) var(--space-1);
		font-size: var(--font-size-2xs);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.08em;
		color: var(--color-text-muted);
	}

	.sidebar ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
	}

	.sidebar a {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-height: 38px;
		padding: 0 var(--space-3);
		font-size: var(--font-size-sm);
		font-weight: 500;
		border-radius: var(--radius-pill);
		color: inherit;
		text-decoration: none;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		transition:
			background var(--duration-fast),
			scale var(--duration-emphasis) var(--ease-spring);
	}

	.sidebar a :global(svg) {
		flex: none;
		color: var(--color-text-muted);
	}

	.sidebar a:hover {
		background: var(--color-hover);
	}

	/* The current page, as in the native navigation: a neutral pill behind
	   the item, its icon in gold. */
	.sidebar a[aria-current] {
		background: var(--color-active-pill);
		font-weight: 650;
	}

	.sidebar a[aria-current] :global(svg) {
		color: var(--color-accent);
	}

	.label {
		overflow: hidden;
		text-overflow: ellipsis;
	}

	/* The rail: icons only, their names in the title and for screen readers. */
	.collapsed .sidebar {
		padding-inline: 10px;
	}

	.collapsed .sidebar a {
		justify-content: center;
		width: 44px;
		height: 44px;
		padding: 0;
		border-radius: 50%;
	}

	.collapsed .sidebar .label {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		white-space: nowrap;
	}

	.collapsed .sidebar ul + h2 {
		margin-top: var(--space-2);
	}

	.collapsed .sidebar ul {
		padding-bottom: var(--space-2);
		border-bottom: 1px solid color-mix(in srgb, currentColor 10%, transparent);
	}

	.collapsed .sidebar ul:last-child {
		border-bottom: 0;
	}

	.sidebar a.drop {
		outline: 2px dashed var(--color-accent);
		background: color-mix(in srgb, var(--color-accent) 15%, transparent);
		scale: 1.03;
	}

	/* ── The page and the capsules over it ── */

	.stage {
		position: relative;
		min-width: 0;
		min-height: 0;
	}

	.topbar {
		--glass-tint: 84%;
		position: absolute;
		inset: var(--float-margin) var(--float-margin) auto var(--page-gutter);
		z-index: 5;
		display: flex;
		align-items: center;
		gap: var(--space-3);
		height: var(--chrome-h);
		/* The page between the capsules stays clickable. */
		pointer-events: none;
	}

	.topbar > :global(*) {
		pointer-events: auto;
	}

	/* A selection's own capsule takes the top: the glass would show these
	   through it. */
	.topbar {
		transition:
			opacity var(--duration-fast),
			visibility var(--duration-fast);
	}

	.selecting .topbar {
		opacity: 0;
		visibility: hidden;
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		width: min(420px, 100%);
		height: 100%;
		padding: 0 var(--space-4);
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-bar);
		color: var(--color-text-muted);
		transition:
			width var(--duration-chrome) var(--ease-spring),
			box-shadow var(--duration-fast);
	}

	/* Grows as it takes the keys. */
	.search:focus-within {
		width: min(620px, 100%);
		box-shadow:
			var(--shadow-bar),
			0 0 0 2px var(--color-focus);
	}

	.palette-hint {
		flex: none;
		padding: 0 var(--space-2);
		border: 1px solid color-mix(in srgb, currentColor 25%, transparent);
		border-radius: var(--radius-xs);
		background: transparent;
		color: inherit;
		font-size: var(--font-size-2xs);
		font-weight: 600;
		line-height: 20px;
		cursor: pointer;
	}

	.palette-hint:hover {
		color: var(--color-text);
	}

	.search input {
		flex: 1;
		min-width: 0;
		padding: var(--space-2) 0;
		border: 0;
		background: transparent;
		color: var(--color-text);
		outline: none;
	}

	.tools {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: 2px;
		height: 100%;
		padding: 0 4px;
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-bar);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border-radius: 50%;
		color: inherit;
	}

	.icon:hover {
		background: var(--color-hover);
	}

	.bell {
		position: relative;
	}

	.uploading {
		position: relative;
		display: flex;
		align-items: center;
		gap: var(--space-1);
		height: var(--control-h-sm);
		padding: 0 var(--space-3);
		border-radius: var(--radius-pill);
		background: var(--color-accent-soft);
		color: var(--color-accent);
		font-size: var(--font-size-xs);
		font-weight: 600;
		font-variant-numeric: tabular-nums;
		text-decoration: none;
		overflow: hidden;
	}

	.uploading:hover {
		box-shadow: inset 0 0 0 100px var(--color-hover);
	}

	.upload-bar {
		position: absolute;
		inset: auto 0 0;
		height: 3px;
	}

	.upload-bar span {
		display: block;
		height: 100%;
		background: var(--color-accent);
		transition: width var(--duration-normal) linear;
	}

	.badge {
		position: absolute;
		top: 2px;
		right: 0;
		min-width: 18px;
		padding: 0 5px;
		border-radius: 9px;
		background: var(--color-badge);
		color: var(--color-badge-text);
		box-shadow: 0 0 0 2px var(--chrome-base);
		font-size: var(--font-size-2xs);
		font-weight: 700;
		line-height: 18px;
		text-align: center;
	}

	/* The account menu's trigger: the user's initial in a gold disc. */
	.tools :global(.account) {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: inherit;
		font: inherit;
		cursor: pointer;
	}

	.tools :global(.account:hover),
	.tools :global(.account.open) {
		background: var(--color-hover);
	}

	.avatar {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		border-radius: 50%;
		background: linear-gradient(135deg, var(--color-primary-fill), var(--color-brand));
		color: var(--color-on-primary-fill);
		font-size: var(--font-size-sm);
		font-weight: 800;
		text-transform: uppercase;
	}

	.identity {
		display: grid;
		gap: 2px;
		min-width: 0;
		max-width: 260px;
	}

	.identity span {
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.content {
		--bleed-top: 0px;
		--bleed-bottom: 0px;
		--bleed-left: 0px;
		height: 100%;
		overflow: auto;
		padding-top: var(--chrome-top);
		outline: none;
	}

	/* A page that takes the whole window (PageChrome bleed): its panes keep
	   the chrome's room inside, so they scroll beneath it. */
	.bleed .content {
		--bleed-top: var(--chrome-top);
		padding-top: 0;
		overflow: hidden;
	}

	/* …and under the navigation island, which floats over it (the island
	   comes first, the page keeps its own stacking: see the map). */
	.wide .content {
		--bleed-left: var(--stage-left);
		width: calc(100% + var(--stage-left));
		margin-left: calc(-1 * var(--stage-left));
	}

	.page-actions {
		display: flex;
		align-items: center;
		gap: 2px;
		height: 100%;
		margin-left: auto;
		padding: 0 4px;
		border-radius: var(--radius-pill);
		box-shadow: var(--shadow-bar);
	}

	/* Right after the page's buttons, the tools close the row. */
	.page-actions + .tools {
		margin-left: 0;
	}

	/* The page's buttons, whatever they were in its header: flat pills in
	   the capsule (a primary or a danger one keeps its colour). */
	.page-actions {
		min-width: 0;
		overflow-x: auto;
		scrollbar-width: none;
	}

	.page-actions :global(.btn) {
		flex: none;
		min-height: calc(var(--chrome-h) - 10px);
		padding: 0 var(--space-3);
		border-color: transparent;
		font-size: var(--font-size-sm);
	}

	.page-actions :global(.btn:not(.primary):not(.danger):not(:hover)) {
		background: transparent;
	}

	.page-actions :global(.icon-btn) {
		flex: none;
	}

	/* A phone has no room for words: buttons with an icon show only it (the
	   text stays their name). */
	@media (max-width: 700px) {
		.page-actions :global(.btn:has(svg)) {
			width: calc(var(--chrome-h) - 8px);
			padding: 0;
			gap: 0;
			justify-content: center;
			border-radius: 50%;
			font-size: 0;
		}
	}

	.brand-pill {
		display: grid;
		place-items: center;
		width: var(--chrome-h);
		height: 100%;
		border-radius: 50%;
		box-shadow: var(--shadow-bar);
	}

	/* Phone-sized: no island; the logo and the tools float at the top, the
	   three tabs at the bottom (FloatingNav), and scrolling down hides both. */
	@media (max-width: 700px) {
		.shell {
			--stage-left: 0px;
			grid-template-columns: minmax(0, 1fr);
		}

		.island {
			display: none;
		}

		.topbar {
			left: var(--float-margin);
			gap: var(--space-2);
			transition:
				translate var(--duration-chrome) var(--ease-standard),
				opacity var(--duration-chrome);
		}

		.immersive .topbar {
			translate: 0 calc(-100% - var(--float-margin) - 8px);
			opacity: 0;
		}

		/* The last of the page clears the tabs. */
		.content {
			--nav-room: calc(
				var(--nav-pill-h) + 8px + var(--float-margin) + env(safe-area-inset-bottom, 0px)
			);
			padding-bottom: var(--nav-room);
			transition:
				padding-top var(--duration-chrome) var(--ease-standard),
				padding-bottom var(--duration-chrome) var(--ease-standard);
		}

		/* A grid scrolled down takes the whole screen; the chrome floats over
		   its photos, and the grid ends with room to clear the tabs. */
		/* Over the photos only the tools stay, top right, as the native
		   timeline's header capsule once scrolled. */
		.reclaimed .brand-pill {
			opacity: 0;
			visibility: hidden;
		}

		.bleed .content {
			--bleed-bottom: var(--nav-room);
			padding-bottom: 0;
		}

		.reclaimed .content {
			--grid-end-room: var(--nav-room);
			padding-top: 0;
			padding-bottom: 0;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.topbar {
			transition: none;
		}
	}
</style>
