<script lang="ts">
	import type { Snippet } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { dropOnAlbum, dropOnFolder, isAssetDrag } from '#lib/actions/drag-assets.js';
	import {
		getAllAlbumsOptions,
		getAllFoldersOptions,
		getUnreadNotificationsCountOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import DropZone from '#lib/account/upload/DropZone.svelte';
	import { uploads } from '#lib/account/upload/uploads.svelte.js';
	import { session } from '#lib/auth/session.svelte.js';
	import { appHref as href } from '#lib/navigation/href.js';
	import { navigation } from '#lib/navigation/sections.js';
	import { initialCollapsed, saveCollapsed } from '#lib/navigation/sidebar.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale, locales, setLocale, type Locale } from '#lib/paraglide/runtime.js';
	import Icon from './Icon.svelte';
	import Logo from './Logo.svelte';

	let { children }: { children: Snippet } = $props();

	const localeNames: Record<Locale, string> = { es: 'Español', en: 'English' };

	const albums = createQuery(() => getAllAlbumsOptions());
	const folders = createQuery(() => getAllFoldersOptions());
	// Polled lightly so new notifications show up without a reload.
	const unread = createQuery(() => ({
		...getUnreadNotificationsCountOptions(),
		refetchInterval: 60_000
	}));
	const unreadCount = $derived(unread.data?.count ?? 0);

	const pinned = $derived([
		...(albums.data ?? [])
			.filter((album) => album.isPinned && album.kind !== 'Smart')
			.map((album) => ({ kind: 'album' as const, id: album.id, name: album.name })),
		...(folders.data ?? [])
			.filter((folder) => folder.isPinned)
			.map((folder) => ({ kind: 'folder' as const, id: folder.id, name: folder.name }))
	]);

	let dropTarget = $state<string | null>(null);
	// A rail of icons or the full menu; narrow windows start as a rail.
	let collapsed = $state(initialCollapsed(window.innerWidth));

	function toggleSidebar() {
		collapsed = !collapsed;
		saveCollapsed(collapsed);
	}
	let search = $state('');

	function isCurrent(path: string) {
		const target = href(path);
		return path === '/' ? page.url.pathname === target : page.url.pathname.startsWith(target);
	}

	async function logout() {
		await session.logout();
		await goto(resolve('/login'), { replace: true });
	}

	function submitSearch(event: SubmitEvent) {
		event.preventDefault();
		const query = search.trim();
		if (query) goto(`${href('/search')}?q=${encodeURIComponent(query)}`);
	}
</script>

<a class="skip-link" href="#content">{m.skip_to_content()}</a>

<!-- Files dropped anywhere in the app go to the upload queue. -->
<DropZone mode="window" onfiles={(files) => uploads.add(files)} />

<div class="shell" class:collapsed>
	<header class="topbar">
		<div class="brand-area">
			<button
				type="button"
				class="icon-btn"
				aria-controls="sidebar"
				aria-expanded={!collapsed}
				aria-label={collapsed ? m.nav_expand() : m.nav_collapse()}
				title={collapsed ? m.nav_expand() : m.nav_collapse()}
				onclick={toggleSidebar}
			>
				<Icon name="menu" />
			</button>
			<a class="brand" href={href('/')} aria-label={m.app_name()}
				><Logo size={28} markOnly={collapsed} /></a
			>
		</div>

		<form class="search" role="search" onsubmit={submitSearch}>
			<Icon name="search" size={18} />
			<input
				type="search"
				aria-label={m.nav_search()}
				placeholder={m.nav_search()}
				bind:value={search}
			/>
		</form>

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
			<details class="account">
				<summary aria-label={m.account_menu()}>
					<span class="avatar" aria-hidden="true"
						>{(session.user?.firstName || session.user?.username || '?').charAt(0)}</span
					>
					<span>{session.user?.username}</span>
				</summary>
				<div class="menu">
					<a href={href('/settings')}>{m.nav_settings()}</a>
					<label>
						<span>{m.language()}</span>
						<select
							value={getLocale()}
							onchange={(event) => setLocale(event.currentTarget.value as Locale)}
						>
							{#each locales as locale (locale)}
								<option value={locale}>{localeNames[locale]}</option>
							{/each}
						</select>
					</label>
					<button type="button" onclick={logout}>{m.account_logout()}</button>
				</div>
			</details>
		</div>
	</header>

	<nav id="sidebar" class="sidebar" aria-label={m.nav_main()}>
		{#each navigation.filter((section) => !section.adminOnly || session.isAdmin) as section (section.id)}
			{#if section.label}
				<h2 class:visually-hidden={collapsed}>{section.label()}</h2>
			{/if}
			<ul>
				{#each section.items as item (item.path)}
					<li>
						<a
							href={href(item.path)}
							aria-current={isCurrent(item.path) ? 'page' : undefined}
							title={collapsed ? item.label() : undefined}
						>
							<Icon name={item.icon} size={18} />
							<span class="label">{item.label()}</span>
						</a>
					</li>
				{/each}
			</ul>
			{#if section.id === 'collections'}
				{#if pinned.length > 0}
					<h2 class:visually-hidden={collapsed}>{m.nav_section_pinned()}</h2>
					<ul>
						{#each pinned as item (item.kind + item.id)}
							{@const path = item.kind === 'album' ? `/albums/${item.id}` : `/folders/${item.id}`}
							<li>
								<a
									href={href(path)}
									class:drop={dropTarget === item.id}
									aria-current={isCurrent(path) ? 'page' : undefined}
									title={item.kind === 'album'
										? m.drop_add_to_album({ album: item.name })
										: m.drop_move_to_folder({ folder: item.name })}
									ondragover={(event) => {
										if (!isAssetDrag(event)) return;
										event.preventDefault();
										dropTarget = item.id;
									}}
									ondragleave={() => (dropTarget = null)}
									ondrop={(event) => {
										event.preventDefault();
										dropTarget = null;
										if (item.kind === 'album') dropOnAlbum(event, item);
										else dropOnFolder(event, item);
									}}
								>
									<Icon name={item.kind === 'album' ? 'album' : 'folder'} size={18} />
									<span class="label">{item.name}</span>
								</a>
							</li>
						{/each}
					</ul>
				{/if}
			{/if}
		{/each}
	</nav>

	<main id="content" class="content" tabindex="-1">
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
		height: 100vh;
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
		/* Lines the search box up with the content column. */
		width: calc(var(--nav-width) - var(--space-4));
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
		max-width: 640px;
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
		color: #fff;
		box-shadow: 0 0 0 2px var(--color-bg);
		font-size: var(--font-size-2xs);
		font-weight: 700;
		line-height: 18px;
		text-align: center;
	}

	.account {
		position: relative;
	}

	.account summary {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		list-style: none;
		cursor: pointer;
		padding: var(--space-1) var(--space-3);
		border-radius: var(--radius-sm);
	}

	.account summary::-webkit-details-marker {
		display: none;
	}

	.account summary:hover {
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

	.menu {
		position: absolute;
		right: 0;
		top: calc(100% + var(--space-1));
		z-index: 5;
		display: grid;
		gap: var(--space-3);
		min-width: 220px;
		padding: var(--space-3);
		background: var(--color-surface-raised);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-raised);
	}

	.menu a {
		color: inherit;
		text-decoration: none;
		padding: var(--space-1) 0;
	}

	.menu label {
		display: grid;
		gap: var(--space-1);
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.menu button {
		padding: var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		cursor: pointer;
		text-align: left;
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

	.sidebar a[aria-current='page'] {
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
</style>
