<script lang="ts">
	import type { Snippet } from 'svelte';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { session } from '#lib/auth/session.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale, locales, setLocale, type Locale } from '#lib/paraglide/runtime.js';

	let { children }: { children: Snippet } = $props();

	const navigation = [{ href: resolve('/(app)'), label: m.nav_photos }] as const;

	const localeNames: Record<Locale, string> = { es: 'Español', en: 'English' };

	function isCurrent(href: string) {
		return href === resolve('/(app)')
			? page.url.pathname === href
			: page.url.pathname.startsWith(href);
	}

	async function logout() {
		await session.logout();
		await goto(resolve('/login'), { replaceState: true });
	}
</script>

<a class="skip-link" href="#content">{m.skip_to_content()}</a>

<div class="shell">
	<header class="topbar">
		<a class="brand" href={resolve('/(app)')}>{m.app_name()}</a>

		<details class="account">
			<summary aria-label={m.account_menu()}>{session.user?.username}</summary>
			<div class="menu">
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
	</header>

	<nav class="sidebar" aria-label={m.nav_main()}>
		<ul>
			{#each navigation as item (item.href)}
				<li>
					<a href={item.href} aria-current={isCurrent(item.href) ? 'page' : undefined}>
						{item.label()}
					</a>
				</li>
			{/each}
		</ul>
	</nav>

	<main id="content" class="content" tabindex="-1">
		{@render children()}
	</main>
</div>

<style>
	.shell {
		display: grid;
		grid-template-columns: var(--sidebar-width) 1fr;
		grid-template-rows: var(--header-height) 1fr;
		grid-template-areas:
			'topbar topbar'
			'sidebar content';
		height: 100vh;
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
		justify-content: space-between;
		padding: 0 var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}

	.brand {
		font-weight: 700;
		font-size: var(--font-size-lg);
		color: inherit;
		text-decoration: none;
	}

	.account {
		position: relative;
	}

	.account summary {
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

	.menu {
		position: absolute;
		right: 0;
		top: calc(100% + var(--space-1));
		z-index: 5;
		display: grid;
		gap: var(--space-3);
		min-width: 200px;
		padding: var(--space-3);
		background: var(--color-surface-raised);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-raised);
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
		padding: var(--space-3);
		overflow-y: auto;
	}

	.sidebar ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-1);
	}

	.sidebar a {
		display: block;
		padding: var(--space-2) var(--space-3);
		border-radius: var(--radius-sm);
		color: inherit;
		text-decoration: none;
	}

	.sidebar a:hover {
		background: var(--color-surface);
	}

	.sidebar a[aria-current='page'] {
		background: var(--color-surface);
		color: var(--color-accent);
		font-weight: 600;
	}

	.content {
		grid-area: content;
		overflow: auto;
		outline: none;
	}
</style>
