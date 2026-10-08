<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { ICON_DONE_ALL } from '#lib/account/icons.js';
	import {
		notificationIcon,
		notificationTarget,
		relativeTime
	} from '#lib/account/notifications/notifications.js';
	import {
		markAllNotificationsRead,
		markNotificationRead,
		type NotificationItemResponse,
		type NotificationsPageResponse
	} from '#lib/api/index.js';
	import {
		getNotificationsOptions,
		getNotificationsQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { dateTime } from '#lib/format.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const PAGE_SIZE = 20;

	const queryClient = useQueryClient();

	// Filter and page live in the URL, so Back and a reload keep the place.
	const unreadOnly = $derived(page.url.searchParams.get('filter') === 'unread');
	const pageNumber = $derived(Math.max(1, Number(page.url.searchParams.get('page')) || 1));
	const options = $derived({
		query: { page: pageNumber, pageSize: PAGE_SIZE, unreadOnly }
	});

	const notifications = createQuery(() => getNotificationsOptions(options));
	const data = $derived(notifications.data);

	function navigateTo(filter: 'all' | 'unread', target = 1) {
		const query = [filter === 'unread' ? 'filter=unread' : '', target > 1 ? `page=${target}` : '']
			.filter(Boolean)
			.join('&');
		goto(appHref('/notifications') + (query ? `?${query}` : ''), { replace: true, reset: false });
	}

	/** Everything that shows notification counts or lists. */
	function invalidate() {
		queryClient.invalidateQueries({ queryKey: [{ _id: 'getNotifications' }] });
		queryClient.invalidateQueries({ queryKey: [{ _id: 'getUnreadNotificationsCount' }] });
	}

	function patchPage(change: (page: NotificationsPageResponse) => NotificationsPageResponse) {
		queryClient.setQueryData<NotificationsPageResponse>(getNotificationsQueryKey(options), (old) =>
			old ? change(old) : old
		);
	}

	async function markRead(item: NotificationItemResponse) {
		if (item.isRead) return;
		patchPage((old) => ({
			...old,
			unreadCount: Math.max(0, old.unreadCount - 1),
			items: old.items.map((n) => (n.id === item.id ? { ...n, isRead: true } : n))
		}));
		const { error, response } = await markNotificationRead({ path: { id: item.id } });
		if (error || !response?.ok) toasts.error(m.action_failed());
		invalidate();
	}

	async function markAllRead() {
		patchPage((old) => ({
			...old,
			unreadCount: 0,
			items: old.items.map((n) => ({ ...n, isRead: true }))
		}));
		const { error, response } = await markAllNotificationsRead();
		if (error || !response?.ok) toasts.error(m.action_failed());
		else toasts.show(m.notifications_all_marked());
		invalidate();
	}

	const locale = getLocale();
</script>

<svelte:head>
	<title>{m.nav_notifications()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<header class="head">
		<h1>{m.nav_notifications()}</h1>
		<button
			type="button"
			class="mark-all"
			disabled={!data || data.unreadCount === 0}
			onclick={markAllRead}
		>
			<Icon path={ICON_DONE_ALL} size={18} />
			{m.notifications_mark_all()}
		</button>
	</header>

	<div class="filters" role="group" aria-label={m.notifications_filter()}>
		<button type="button" aria-pressed={!unreadOnly} onclick={() => navigateTo('all')}>
			{m.notifications_filter_all()}
		</button>
		<button type="button" aria-pressed={unreadOnly} onclick={() => navigateTo('unread')}>
			{m.notifications_filter_unread()}
			{#if data && data.unreadCount > 0}<span class="count">{data.unreadCount}</span>{/if}
		</button>
	</div>

	{#if notifications.isPending}
		<p class="status" role="status">{m.session_restoring()}</p>
	{:else if notifications.isError || !data}
		<p class="status" role="alert">{m.error_loading()}</p>
	{:else if data.items.length === 0}
		<div class="empty">
			<Icon name="notifications" size={40} />
			<p class="empty-title">
				{unreadOnly ? m.notifications_empty_unread() : m.notifications_empty()}
			</p>
			<p>{m.notifications_empty_hint()}</p>
		</div>
	{:else}
		<ul class="list" aria-label={m.nav_notifications()}>
			{#each data.items as item (item.id)}
				{@const icon = notificationIcon(item.type)}
				{@const target = notificationTarget(item.actionUrl)}
				<li class:unread={!item.isRead}>
					<span class="icon {icon.tone}" aria-hidden="true"><Icon path={icon.path} /></span>
					<div class="text">
						{#if target}
							<a class="title" href={appHref(target)} onclick={() => markRead(item)}>
								{item.title}
							</a>
						{:else}
							<span class="title">{item.title}</span>
						{/if}
						<p class="message">{item.message}</p>
						<p class="meta">
							<time datetime={item.createdAt} title={dateTime(item.createdAt)}>
								{relativeTime(item.createdAt, locale)}
							</time>
							{#if item.groupCount > 1}
								· {m.notifications_grouped({ count: item.groupCount })}
							{/if}
							{#if !item.isRead}<span class="visually-hidden">· {m.notifications_unread()}</span
								>{/if}
						</p>
					</div>
					{#if !item.isRead}
						<button
							type="button"
							class="read"
							title={m.notifications_mark_read()}
							aria-label={m.notifications_mark_read_one({ title: item.title })}
							onclick={() => markRead(item)}
						>
							<span class="dot" aria-hidden="true"></span>
						</button>
					{/if}
				</li>
			{/each}
		</ul>

		<nav class="pager" aria-label={m.notifications_pages()}>
			{#if data.totalPages > 1}
				<button
					type="button"
					disabled={pageNumber <= 1}
					onclick={() => navigateTo(unreadOnly ? 'unread' : 'all', pageNumber - 1)}
				>
					<Icon name="chevronLeft" size={18} />
					{m.notifications_previous()}
				</button>
				<span>{m.notifications_page({ page: pageNumber, pages: data.totalPages })}</span>
				<button
					type="button"
					disabled={pageNumber >= data.totalPages}
					onclick={() => navigateTo(unreadOnly ? 'unread' : 'all', pageNumber + 1)}
				>
					{m.notifications_next()}
					<Icon name="chevronRight" size={18} />
				</button>
			{/if}
			<span class="total">{m.notifications_total({ count: data.totalCount })}</span>
		</nav>
	{/if}
</div>

<style>
	.page {
		display: grid;
		gap: var(--space-4);
		max-width: 820px;
		padding: var(--space-4) var(--space-6) var(--space-8);
	}

	.head {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	h1 {
		margin: 0;
		font-size: var(--font-size-xl);
	}

	button {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface-raised);
		cursor: pointer;
		font-size: var(--font-size-sm);
	}

	button:hover:not(:disabled) {
		background: var(--color-surface);
	}

	button:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.mark-all {
		margin-left: auto;
	}

	.filters {
		display: flex;
		gap: var(--space-2);
	}

	.filters button {
		border-radius: 999px;
		padding: var(--space-1) var(--space-4);
	}

	.filters button[aria-pressed='true'] {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
	}

	.count {
		min-width: 1.4em;
		padding: 0 6px;
		border-radius: 999px;
		background: color-mix(in srgb, currentColor 18%, transparent);
		font-size: var(--font-size-xs);
		text-align: center;
	}

	.status {
		color: var(--color-text-muted);
	}

	.empty {
		display: grid;
		justify-items: center;
		gap: var(--space-1);
		padding: var(--space-8) var(--space-4);
		color: var(--color-text-muted);
		text-align: center;
	}

	.empty p {
		margin: 0;
	}

	.empty-title {
		margin-top: var(--space-2) !important;
		color: var(--color-text);
		font-weight: 600;
	}

	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
		overflow: hidden;
	}

	li {
		position: relative;
		display: flex;
		align-items: flex-start;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
	}

	li + li {
		border-top: 1px solid var(--color-border);
	}

	li.unread {
		background: color-mix(in srgb, var(--color-accent) 6%, transparent);
	}

	li:hover {
		background: var(--color-surface);
	}

	.icon {
		display: grid;
		place-items: center;
		flex: none;
		width: 36px;
		height: 36px;
		border-radius: 50%;
		background: var(--color-surface);
	}

	.icon.accent {
		color: var(--color-accent);
	}

	.icon.danger {
		color: var(--color-danger);
	}

	.icon.muted {
		color: var(--color-text-muted);
	}

	.text {
		flex: 1;
		min-width: 0;
	}

	.title {
		color: inherit;
		text-decoration: none;
	}

	.unread .title {
		font-weight: 600;
	}

	a.title::after {
		/* The whole row is the link's hit area; the read button sits above it. */
		content: '';
		position: absolute;
		inset: 0;
	}

	a.title:focus-visible {
		outline: none;
	}

	li:has(a.title:focus-visible) {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	.message,
	.meta {
		margin: 2px 0 0;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.meta {
		font-size: var(--font-size-xs);
	}

	.read {
		position: relative;
		z-index: 1;
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
	}

	.dot {
		width: 10px;
		height: 10px;
		border-radius: 50%;
		background: var(--color-accent);
	}

	.pager {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--space-3);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.total {
		margin-left: auto;
	}
</style>
