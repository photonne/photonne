<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getUnreadNotificationsCountOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import TileGrid, { type Tile } from '#lib/components/ui/TileGrid.svelte';
	import { session } from '#lib/auth/session.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { navigation } from '#lib/navigation/sections.js';
	import { m } from '#lib/paraglide/messages.js';

	// The «Más» tab of the phone navigation: the actions, the account and,
	// for admins, the administration.
	const unread = createQuery(() => getUnreadNotificationsCountOptions());

	const actions: Tile[] = navigation
		.find((section) => section.id === 'actions')!
		.items.map((item) => ({ href: appHref(item.path), label: item.label(), icon: item.icon }));

	const account: Tile[] = $derived([
		{
			href: appHref('/notifications'),
			label: m.nav_notifications(),
			icon: 'notifications',
			badge: unread.data?.count ?? 0
		},
		{ href: appHref('/settings'), label: m.nav_settings(), icon: 'settings' }
	]);

	const admin: Tile[] = [{ href: appHref('/admin'), label: m.nav_admin(), icon: 'shield' }];
</script>

<svelte:head>
	<title>{m.nav_more()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<PageHeader title={m.nav_more()} />
	<div class="sections">
		<TileGrid title={m.nav_section_actions()} tiles={actions} />
		<TileGrid title={m.nav_section_account()} tiles={account} />
		{#if session.isAdmin}
			<TileGrid title={m.nav_admin()} tiles={admin} />
		{/if}
	</div>
</div>

<style>
	.sections {
		display: grid;
		gap: var(--space-8);
		padding: var(--space-2) var(--page-gutter) var(--space-8);
	}
</style>
