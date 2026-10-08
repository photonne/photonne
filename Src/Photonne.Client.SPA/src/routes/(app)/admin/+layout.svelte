<script lang="ts">
	import { page } from '$app/state';
	import { session } from '#lib/auth/session.svelte.js';
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
</script>

{#if session.isAdmin}
	<div class="admin">
		<nav aria-label={m.admin_nav()}>
			{#each sections as section (section.path)}
				<a href={appHref(section.path)} aria-current={isCurrent(section.path) ? 'page' : undefined}
					>{section.label()}</a
				>
			{/each}
		</nav>
		<div class="body">{@render children()}</div>
	</div>
{:else}
	<p class="denied" role="alert">{m.admin_only()}</p>
{/if}

<style>
	.admin {
		display: flex;
		flex-direction: column;
		min-height: 100%;
	}

	nav {
		position: sticky;
		top: 0;
		z-index: 2;
		display: flex;
		gap: var(--space-1);
		overflow-x: auto;
		padding: var(--space-2) var(--space-4);
		background: var(--color-bg);
		border-bottom: 1px solid var(--color-border);
	}

	nav a {
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		color: inherit;
		text-decoration: none;
		white-space: nowrap;
	}

	nav a:hover {
		background: var(--color-surface);
	}

	nav a[aria-current='page'] {
		background: var(--color-surface);
		color: var(--color-accent);
		font-weight: 600;
	}

	.body {
		flex: 1;
	}

	.denied {
		padding: var(--space-6);
		color: var(--color-text-muted);
	}
</style>
