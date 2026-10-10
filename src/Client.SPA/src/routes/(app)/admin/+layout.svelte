<script lang="ts">
	import { page } from '$app/state';
	import { session } from '#lib/auth/session.svelte.js';
	import Tabs from '#lib/components/ui/Tabs.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const sections = [
		{ path: '/admin', label: m.admin_dashboard },
		{ path: '/admin/users', label: m.admin_users },
		{ path: '/admin/libraries', label: m.admin_libraries },
		{ path: '/admin/tasks', label: m.admin_tasks },
		{ path: '/admin/maintenance', label: m.admin_maintenance },
		{ path: '/admin/settings', label: m.admin_settings },
		{ path: '/admin/backup', label: m.admin_backup },
		{ path: '/admin/shared-trash', label: m.admin_shared_trash },
		{ path: '/admin/system', label: m.admin_system }
	];

	function isCurrent(path: string) {
		const href = appHref(path);
		return path === '/admin' ? page.url.pathname === href : page.url.pathname.startsWith(href);
	}

	const tabs = $derived(
		sections.map((section) => ({
			href: appHref(section.path),
			label: section.label(),
			current: isCurrent(section.path)
		}))
	);
</script>

{#if session.isAdmin}
	<div class="admin">
		<div class="nav"><Tabs {tabs} label={m.admin_nav()} /></div>
		<div class="body">{@render children()}</div>
	</div>
{:else}
	<p class="denied" role="alert">{m.admin_only()}</p>
{/if}

<style>
	.admin {
		/* Pages under it pin their own sticky bars right below the tabs: the
		   tabs' 44 px row plus their 1 px rule (Tabs.svelte). */
		--admin-nav-height: 45px;
		display: flex;
		flex-direction: column;
		min-height: 100%;
	}

	.nav {
		position: sticky;
		top: 0;
		z-index: 3;
		height: var(--admin-nav-height);
	}

	.body {
		flex: 1;
	}

	.denied {
		padding: var(--space-6) var(--page-gutter);
		color: var(--color-text-muted);
	}
</style>
