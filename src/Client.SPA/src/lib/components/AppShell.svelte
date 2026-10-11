<script lang="ts">
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import PopupMenu, { type MenuEntry } from '#lib/components/ui/PopupMenu.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { tick, type Snippet } from 'svelte';
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

	// On a phone-sized window the menu is a drawer over the page instead.
	const DRAWER_QUERY = '(max-width: 700px)';
	let mobile = $state(window.matchMedia(DRAWER_QUERY).matches);
	let drawerOpen = $state(false);
	let menuButton = $state<HTMLButtonElement>();
	let sidebar = $state<HTMLElement>();
	/** Icons only: the rail, never in the drawer. */
	const rail = $derived(collapsed && !mobile);

	$effect(() => {
		const query = window.matchMedia(DRAWER_QUERY);
		const follow = () => {
			mobile = query.matches;
			if (!mobile) drawerOpen = false;
		};
		query.addEventListener('change', follow);
		return () => query.removeEventListener('change', follow);
	});

	afterNavigate(() => (drawerOpen = false));

	function toggleSidebar() {
		if (mobile) {
			drawerOpen = !drawerOpen;
			// Into the menu on opening, so the keyboard lands where the eyes do.
			if (drawerOpen) tick().then(() => sidebar?.querySelector('a')?.focus());
			return;
		}
		collapsed = !collapsed;
		chosen = true;
		saveCollapsed(collapsed);
	}

	const menuLabel = $derived(
		mobile
			? drawerOpen
				? m.nav_close_menu()
				: m.nav_open_menu()
			: collapsed
				? m.nav_expand()
				: m.nav_collapse()
	);

	function closeDrawer() {
		drawerOpen = false;
		menuButton?.focus();
	}

	function onkeydown(event: KeyboardEvent) {
		if (drawerOpen && event.key === 'Escape' && !event.defaultPrevented) {
			event.preventDefault();
			closeDrawer();
		}
	}
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

<svelte:window {onkeydown} />

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

<div class="shell" class:collapsed={rail} class:drawer-open={drawerOpen}>
	<header class="topbar">
		<div class="brand-area">
			<button
				bind:this={menuButton}
				type="button"
				class="icon-btn"
				aria-controls="sidebar"
				aria-expanded={mobile ? drawerOpen : !collapsed}
				aria-label={menuLabel}
				title={menuLabel}
				onclick={toggleSidebar}
			>
				<Icon name="menu" />
			</button>
			<a class="brand" href={href('/')} aria-label={m.app_name()}
				><Logo size={28} markOnly={rail || mobile} /></a
			>
		</div>

		{#if !onSearchPage}
			<form class="search" role="search" onsubmit={submitSearch}>
				<Icon name="search" size={18} />
				<input
					type="search"
					class="bare"
					aria-label={m.nav_search()}
					placeholder={m.nav_search()}
					bind:value={search}
				/>
			</form>
		{/if}

		<div class="tools">
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
					<span class="username">{session.user?.username}</span>
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

	{#if drawerOpen}
		<!-- The page behind the drawer: a click on it closes the drawer. -->
		<div class="scrim" aria-hidden="true" onclick={closeDrawer}></div>
	{/if}

	<nav id="sidebar" class="sidebar" aria-label={m.nav_main()} bind:this={sidebar}>
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
							<Icon name={item.icon} size={18} />
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
										size={18}
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

	<main id="content" class="content" tabindex="-1" inert={drawerOpen}>
		{@render children()}
	</main>
</div>

<style>
	.shell {
		--nav-width: var(--sidebar-width);
		display: grid;
		grid-template-columns: var(--nav-width) minmax(0, 1fr);
		grid-template-rows: var(--header-height) 1fr;
		grid-template-areas:
			'topbar topbar'
			'sidebar content';
		height: 100dvh;
		transition: grid-template-columns var(--duration-normal) ease;
	}

	.shell.collapsed {
		--nav-width: 68px;
	}

	.skip-link {
		position: absolute;
		left: var(--space-2);
		top: -100px;
		z-index: 10;
		padding: var(--space-2) var(--space-3);
		background: var(--color-surface-raised);
		border-radius: var(--radius-sm);
	}

	.skip-link:focus {
		top: var(--space-2);
	}

	.topbar {
		grid-area: topbar;
		display: flex;
		align-items: center;
		gap: var(--space-4);
		padding: 0 var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}

	.brand-area {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		/* Lines the search box up with the content column; on the rail the
		   button and the logo need more than its width, so it grows to fit. */
		width: calc(var(--nav-width) - var(--space-4));
		min-width: max-content;
		flex: none;
		transition: width var(--duration-normal) ease;
	}

	.brand {
		display: flex;
		align-items: center;
		min-width: 0;
		color: inherit;
		text-decoration: none;
	}

	.search {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: 0 var(--space-3);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		color: var(--color-text-muted);
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

	.search:focus-within {
		outline: 2px solid var(--color-focus);
	}

	.tools {
		margin-left: auto;
		display: flex;
		align-items: center;
		gap: var(--space-2);
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
		background: var(--color-surface);
	}

	.bell {
		position: relative;
	}

	.badge {
		position: absolute;
		top: 4px;
		right: 2px;
		min-width: 18px;
		padding: 0 5px;
		border-radius: 9px;
		background: var(--color-badge);
		color: var(--color-badge-text);
		box-shadow: 0 0 0 2px var(--color-bg);
		font-size: var(--font-size-2xs);
		font-weight: 700;
		line-height: 18px;
		text-align: center;
	}

	/* The account menu's trigger: an avatar and the user's name. */
	.tools :global(.account) {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: transparent;
		color: inherit;
		font: inherit;
		cursor: pointer;
	}

	.tools :global(.account:hover),
	.tools :global(.account.open) {
		background: var(--color-surface);
	}

	.avatar {
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		border-radius: 50%;
		background: var(--color-accent-soft);
		color: var(--color-accent);
		font-size: var(--font-size-sm);
		font-weight: 700;
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

	.sidebar {
		grid-area: sidebar;
		overflow-x: hidden;
		padding: var(--space-2) var(--space-3) var(--space-6);
		overflow-y: auto;
	}

	.sidebar h2 {
		margin: var(--space-4) var(--space-3) var(--space-1);
		font-size: var(--font-size-2xs);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
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
		min-height: 36px;
		padding: 0 var(--space-3);
		font-size: var(--font-size-sm);
		font-weight: 500;
		border-radius: var(--radius-control);
		color: inherit;
		text-decoration: none;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.sidebar a:hover {
		background: var(--color-surface);
	}

	.sidebar a[aria-current] {
		background: var(--color-accent-soft);
		color: var(--color-accent);
		font-weight: 600;
	}

	/* The rail: icons only, their names in the title and for screen readers. */
	.collapsed .sidebar {
		padding-inline: var(--space-2);
	}

	.collapsed .sidebar a {
		justify-content: center;
		padding: 0;
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
		border-bottom: 1px solid var(--color-border);
	}

	.sidebar a.drop {
		outline: 2px dashed var(--color-accent);
		background: color-mix(in srgb, var(--color-accent) 15%, transparent);
	}

	.content {
		grid-area: content;
		overflow: auto;
		outline: none;
	}

	.scrim {
		display: none;
	}

	/* Phone-sized: one column, the menu slides in over the page. */
	@media (max-width: 700px) {
		.shell {
			grid-template-columns: minmax(0, 1fr);
			grid-template-areas:
				'topbar'
				'content';
		}

		.topbar {
			gap: var(--space-2);
			padding: 0 var(--space-2);
		}

		.brand-area {
			width: auto;
		}

		.tools {
			gap: 0;
		}

		.tools :global(.account) {
			padding: var(--space-1);
		}

		.username {
			display: none;
		}

		.sidebar {
			position: fixed;
			top: var(--header-height);
			bottom: 0;
			left: 0;
			z-index: 20;
			width: min(300px, 85vw);
			background: var(--color-bg);
			border-right: 1px solid var(--color-border);
			box-shadow: var(--shadow-raised);
			transform: translateX(-100%);
			visibility: hidden;
			transition:
				transform var(--duration-normal) ease,
				visibility var(--duration-normal);
		}

		/* Visible at once on opening (focus can go in); hidden only once it
		   has slid out on closing. */
		.drawer-open .sidebar {
			transform: none;
			visibility: visible;
			transition: transform var(--duration-normal) ease;
		}

		.scrim {
			display: block;
			position: fixed;
			inset: var(--header-height) 0 0;
			z-index: 19;
			background: var(--color-scrim-soft);
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.sidebar {
			transition: none;
		}
	}
</style>
