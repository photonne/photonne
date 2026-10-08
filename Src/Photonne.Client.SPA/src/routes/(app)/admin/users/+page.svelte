<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { deleteUser, promoteToPrimaryAdmin, updateUser, type UserDto } from '#lib/api/index.js';
	import {
		getAdminStatsOptions,
		getAllUsersOptions,
		getAllUsersQueryKey,
		getCurrentUserOptions,
		getCurrentUserQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import AdminPage from '#lib/admin/AdminPage.svelte';
	import ConfirmDialog from '#lib/admin/ConfirmDialog.svelte';
	import { errorText } from '#lib/admin/errors.js';
	import { count, localDate, localDateTime, relativeTime } from '#lib/admin/format.js';
	import { adminIcons } from '#lib/admin/icons.js';
	import ResetPasswordDialog from '#lib/admin/ResetPasswordDialog.svelte';
	import UserDialog from '#lib/admin/UserDialog.svelte';
	import {
		canChangeRoleOrStatus,
		canDelete,
		canPromote,
		displayName,
		initials,
		matchesQuery,
		nextSort,
		quotaShare,
		sortUsers,
		type AdminUser,
		type SortDirection,
		type UserSortKey
	} from '#lib/admin/users.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { formatBytes } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';

	type Filter = 'all' | 'admins' | 'users' | 'inactive';

	const queryClient = useQueryClient();
	const usersQuery = createQuery(() => getAllUsersOptions());
	// Storage per user comes from the stats; the table works without it.
	const statsQuery = createQuery(() => getAdminStatsOptions());
	// A fresh "me": whether I'm the primary admin can change while signed in.
	const meQuery = createQuery(() => getCurrentUserOptions());
	const me = $derived(meQuery.data ?? session.user);

	let search = $state('');
	let filter = $state<Filter>('all');
	let sort = $state<{ key: UserSortKey; direction: SortDirection }>({
		key: 'name',
		direction: 'asc'
	});

	let editing = $state<{ user: UserDto | null } | null>(null);
	let resetting = $state<UserDto | null>(null);
	let confirming = $state<{ kind: 'delete' | 'promote'; user: AdminUser } | null>(null);
	let busy = $state(false);

	const users = $derived.by((): AdminUser[] => {
		const usage = new Map(
			(statsQuery.data?.users ?? []).map((u) => [u.userId, u.photoBytes + u.videoBytes])
		);
		return (usersQuery.data ?? []).map((u) => ({
			...u,
			id: u.id ?? '',
			username: u.username ?? '',
			usedBytes: usage.get(u.id ?? '') ?? (statsQuery.data ? 0 : null)
		}));
	});

	const totals = $derived({
		all: users.length,
		admins: users.filter((u) => u.role === 'Admin').length,
		inactive: users.filter((u) => !u.isActive).length
	});

	const filters = $derived<{ value: Filter; label: string; total: number }[]>([
		{ value: 'all', label: m.admin_users_filter_all(), total: totals.all },
		{ value: 'admins', label: m.admin_users_filter_admins(), total: totals.admins },
		{ value: 'users', label: m.admin_users_filter_users(), total: totals.all - totals.admins },
		{ value: 'inactive', label: m.admin_users_filter_inactive(), total: totals.inactive }
	]);

	const rows = $derived(
		sortUsers(
			users.filter(
				(u) =>
					matchesQuery(u, search) &&
					(filter === 'all' ||
						(filter === 'admins' && u.role === 'Admin') ||
						(filter === 'users' && u.role !== 'Admin') ||
						(filter === 'inactive' && !u.isActive))
			),
			sort.key,
			sort.direction
		)
	);

	const columns: { key: UserSortKey; label: () => string; class?: string }[] = [
		{ key: 'name', label: m.admin_users_col_user },
		{ key: 'role', label: m.admin_users_col_role, class: 'role-col' },
		{ key: 'status', label: m.admin_users_col_status, class: 'status-col' },
		{ key: 'usage', label: m.admin_users_col_storage },
		{ key: 'lastLogin', label: m.admin_users_col_last_login, class: 'wide' },
		{ key: 'created', label: m.admin_users_col_created, class: 'wider' }
	];

	function refresh() {
		queryClient.invalidateQueries({ queryKey: getAllUsersQueryKey() });
		queryClient.invalidateQueries({ queryKey: getCurrentUserQueryKey() });
	}

	function saved(user: UserDto, created: boolean) {
		editing = null;
		toasts.show(
			created
				? m.admin_users_created({ name: user.username ?? '' })
				: m.admin_users_updated({ name: user.username ?? '' })
		);
		refresh();
	}

	async function setActive(user: AdminUser, isActive: boolean, offerUndo = true) {
		const { error } = await updateUser({ path: { id: user.id }, body: { isActive } });
		if (error) {
			toasts.error(errorText(error));
			return;
		}
		refresh();
		toasts.show(
			isActive
				? m.admin_users_activated({ name: user.username })
				: m.admin_users_deactivated({ name: user.username }),
			offerUndo
				? { action: { label: m.action_undo(), run: () => setActive(user, !isActive, false) } }
				: {}
		);
	}

	async function confirm() {
		if (!confirming) return;
		const { kind, user } = confirming;
		busy = true;
		const { error } =
			kind === 'delete'
				? await deleteUser({ path: { id: user.id } })
				: await promoteToPrimaryAdmin({ path: { id: user.id } });
		busy = false;
		confirming = null;
		if (error) {
			toasts.error(errorText(error));
			return;
		}
		toasts.show(
			kind === 'delete'
				? m.admin_users_deleted({ name: user.username })
				: m.admin_users_promoted({ name: user.username })
		);
		refresh();
	}

	function sortLabel(key: UserSortKey) {
		if (sort.key !== key) return undefined;
		return sort.direction === 'asc' ? 'ascending' : 'descending';
	}
</script>

<AdminPage title={m.admin_users()} description={m.admin_users_description()}>
	{#snippet actions()}
		<button type="button" class="btn primary" onclick={() => (editing = { user: null })}>
			<Icon path={adminIcons.personAdd} size={18} />
			{m.admin_users_new()}
		</button>
	{/snippet}

	<div class="toolbar">
		<label class="search">
			<span class="visually-hidden">{m.admin_users_search()}</span>
			<Icon name="search" size={18} />
			<input type="search" placeholder={m.admin_users_search()} bind:value={search} />
		</label>
		<div class="filters" role="group" aria-label={m.admin_users_filter()}>
			{#each filters as { value, label, total } (value)}
				<button
					type="button"
					aria-pressed={filter === value}
					class="filter"
					onclick={() => (filter = value)}
				>
					{label} <span class="num">{count(total)}</span>
				</button>
			{/each}
		</div>
	</div>

	{#if usersQuery.isPending}
		<p class="muted" role="status">{m.session_restoring()}</p>
	{:else if usersQuery.isError}
		<p class="notice danger" role="alert">{m.error_loading()}</p>
	{:else}
		<div class="table-wrap">
			<table class="data">
				<caption class="visually-hidden">{m.admin_users()}</caption>
				<thead>
					<tr>
						{#each columns as column (column.key)}
							<th scope="col" class={column.class} aria-sort={sortLabel(column.key)}>
								<button
									type="button"
									class="sort"
									onclick={() => (sort = nextSort(sort, column.key))}
								>
									{column.label()}
									{#if sort.key === column.key}
										<Icon
											path={sort.direction === 'asc' ? adminIcons.arrowUp : adminIcons.arrowDown}
											size={14}
										/>
									{/if}
								</button>
							</th>
						{/each}
						<th scope="col" class="actions-col"
							><span class="visually-hidden">{m.admin_core_actions()}</span></th
						>
					</tr>
				</thead>
				<tbody>
					{#each rows as user (user.id)}
						{@const share = quotaShare(user.usedBytes, user.storageQuotaBytes)}
						{@const isMe = user.id === me?.id}
						<tr class:inactive={!user.isActive}>
							<td>
								<div class="who">
									<span class="avatar" class:admin={user.role === 'Admin'} aria-hidden="true"
										>{initials(user)}</span
									>
									<div class="names">
										<button
											type="button"
											class="name"
											title={m.admin_users_edit_named({ name: user.username })}
											onclick={() => (editing = { user })}
										>
											{displayName(user)}
										</button>
										<span class="sub muted">
											{displayName(user) !== user.username
												? `@${user.username} · ${user.email}`
												: user.email}
										</span>
									</div>
									{#if isMe}<span class="badge accent">{m.admin_users_you()}</span>{/if}
									<span class="inline">
										{#if user.isPrimaryAdmin}
											<span class="badge warning">{m.admin_users_primary()}</span>
										{:else if user.role === 'Admin'}
											<span class="badge danger">{m.admin_core_role_admin()}</span>
										{/if}
										{#if !user.isActive}
											<span class="badge">{m.admin_users_inactive()}</span>
										{/if}
									</span>
								</div>
							</td>
							<td class="role-col">
								<div class="role">
									<span class="badge" class:danger={user.role === 'Admin'}>
										{user.role === 'Admin' ? m.admin_core_role_admin() : m.admin_core_role_user()}
									</span>
									{#if user.isPrimaryAdmin}
										<span class="badge warning" title={m.admin_users_primary_hint()}>
											<Icon name="shield" size={12} />
											{m.admin_users_primary()}
										</span>
									{/if}
								</div>
							</td>
							<td class="status-col">
								<span class="badge" class:success={user.isActive}>
									{user.isActive ? m.admin_users_active() : m.admin_users_inactive()}
								</span>
							</td>
							<td class="storage">
								{#if user.usedBytes !== null}
									<span class="num">{formatBytes(user.usedBytes)}</span>
									{#if user.storageQuotaBytes}
										<span class="muted"> / {formatBytes(user.storageQuotaBytes)}</span>
									{/if}
								{:else}
									<span class="muted">—</span>
								{/if}
								{#if share !== null}
									<div
										class="bar"
										class:warning={share >= 0.8 && share < 1}
										class:danger={share >= 1}
										role="meter"
										aria-valuemin="0"
										aria-valuemax="100"
										aria-valuenow={Math.round(share * 100)}
										aria-label={m.admin_users_quota_used({ name: user.username })}
									>
										<span style:width="{share * 100}%"></span>
									</div>
								{:else if user.usedBytes !== null}
									<span class="muted small"> · {m.admin_users_no_quota()}</span>
								{/if}
							</td>
							<td class="wide nowrap">
								{#if user.lastLoginAt}
									<time datetime={user.lastLoginAt} title={localDateTime(user.lastLoginAt)}
										>{relativeTime(user.lastLoginAt)}</time
									>
								{:else}
									<span class="muted">{m.admin_users_never()}</span>
								{/if}
							</td>
							<td class="wider">
								{#if user.createdAt}<time datetime={user.createdAt}
										>{localDate(user.createdAt)}</time
									>{/if}
							</td>
							<td class="actions-col">
								<div class="row-actions">
									<button
										type="button"
										class="icon-btn"
										aria-label={m.admin_users_edit_named({ name: user.username })}
										title={m.admin_core_edit()}
										onclick={() => (editing = { user })}
									>
										<Icon name="edit" size={18} />
									</button>
									<button
										type="button"
										class="icon-btn"
										aria-label={m.admin_users_reset_named({ name: user.username })}
										title={m.admin_users_reset()}
										onclick={() => (resetting = user)}
									>
										<Icon path={adminIcons.key} size={18} />
									</button>
									{#if canChangeRoleOrStatus(user, me)}
										<button
											type="button"
											class="icon-btn"
											aria-label={user.isActive
												? m.admin_users_deactivate_named({ name: user.username })
												: m.admin_users_activate_named({ name: user.username })}
											title={user.isActive ? m.admin_users_deactivate() : m.admin_users_activate()}
											onclick={() => setActive(user, !user.isActive)}
										>
											<Icon
												path={user.isActive ? adminIcons.personOff : adminIcons.personCheck}
												size={18}
											/>
										</button>
									{:else}
										<span class="slot"></span>
									{/if}
									{#if canPromote(user, me)}
										<button
											type="button"
											class="icon-btn"
											aria-label={m.admin_users_promote_named({ name: user.username })}
											title={m.admin_users_promote()}
											onclick={() => (confirming = { kind: 'promote', user })}
										>
											<Icon name="shield" size={18} />
										</button>
									{:else}
										<span class="slot"></span>
									{/if}
									{#if canDelete(user, me)}
										<button
											type="button"
											class="icon-btn danger"
											aria-label={m.admin_users_delete_named({ name: user.username })}
											title={m.admin_core_delete()}
											onclick={() => (confirming = { kind: 'delete', user })}
										>
											<Icon name="delete" size={18} />
										</button>
									{:else}
										<span class="slot"></span>
									{/if}
								</div>
							</td>
						</tr>
					{:else}
						<tr>
							<td colspan={columns.length + 1} class="empty muted">
								{users.length ? m.admin_users_no_match() : m.admin_users_empty()}
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		</div>
		<p class="muted small" aria-live="polite">
			{m.admin_users_summary({ shown: rows.length, total: users.length })}
		</p>
	{/if}

	<UserDialog
		open={editing !== null}
		user={editing?.user ?? null}
		{me}
		onclose={() => (editing = null)}
		onsaved={saved}
	/>

	<ResetPasswordDialog
		user={resetting}
		onclose={() => (resetting = null)}
		ondone={(user) => {
			resetting = null;
			toasts.show(m.admin_users_reset_done({ name: user.username ?? '' }));
		}}
	/>

	<ConfirmDialog
		open={confirming !== null}
		title={confirming?.kind === 'promote'
			? m.admin_users_promote_title()
			: m.admin_users_delete_title()}
		confirmLabel={confirming?.kind === 'promote'
			? m.admin_users_promote_confirm()
			: m.admin_core_delete()}
		tone={confirming?.kind === 'promote' ? 'primary' : 'danger'}
		{busy}
		onconfirm={confirm}
		onclose={() => (confirming = null)}
	>
		{#if confirming?.kind === 'promote'}
			<p>{m.admin_users_promote_body({ name: confirming.user.username })}</p>
			<p class="muted small">{m.admin_users_promote_note()}</p>
		{:else if confirming}
			<p>{m.admin_users_delete_body({ name: confirming.user.username })}</p>
			<p class="muted small">{m.admin_users_delete_note()}</p>
		{/if}
	</ConfirmDialog>
</AdminPage>

<style>
	.toolbar {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	.search {
		flex: 1 1 260px;
		max-width: 420px;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: 0 var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		color: var(--color-text-muted);
	}

	.search:focus-within {
		outline: 2px solid var(--color-focus);
		outline-offset: 1px;
	}

	.search input {
		flex: 1;
		min-width: 0;
		min-height: 36px;
		border: 0;
		background: transparent;
		outline: none;
		color: var(--color-text);
	}

	.filters {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
	}

	.filter {
		padding: var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: 999px;
		background: transparent;
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.filter .num {
		color: var(--color-text-muted);
	}

	.filter[aria-pressed='true'] {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	.filter[aria-pressed='true'] .num {
		color: inherit;
		opacity: 0.85;
	}

	th .sort {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		padding: 0;
		border: 0;
		background: none;
		font: inherit;
		letter-spacing: inherit;
		text-transform: inherit;
		color: inherit;
		cursor: pointer;
	}

	th[aria-sort] {
		color: var(--color-text);
	}

	.who {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		min-width: 220px;
	}

	.avatar {
		flex: none;
		display: grid;
		place-items: center;
		width: 34px;
		height: 34px;
		border-radius: 50%;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-size: var(--font-size-xs);
		font-weight: 700;
	}

	.avatar.admin {
		background: var(--color-danger);
		color: #fff;
	}

	.names {
		display: grid;
		min-width: 120px;
	}

	.inline {
		display: none;
		flex-wrap: wrap;
		gap: 4px;
	}

	.name {
		justify-self: start;
		max-width: 100%;
		padding: 0;
		border: 0;
		background: none;
		font-weight: 600;
		text-align: left;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		cursor: pointer;
	}

	.name:hover {
		text-decoration: underline;
	}

	.sub {
		font-size: var(--font-size-xs);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	tr.inactive .names,
	tr.inactive .avatar {
		opacity: 0.6;
	}

	.storage {
		min-width: 150px;
		white-space: nowrap;
	}

	.storage .bar {
		margin-top: 4px;
		max-width: 160px;
	}

	.slot {
		width: 32px;
	}

	.role {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.nowrap {
		white-space: nowrap;
	}

	.actions-col {
		width: 1%;
		white-space: nowrap;
	}

	.row-actions {
		display: flex;
		justify-content: flex-end;
		gap: 2px;
	}

	.empty {
		padding: var(--space-6);
		text-align: center;
	}

	p {
		margin: 0;
	}

	@media (max-width: 1280px) {
		.wider {
			display: none;
		}
	}

	@media (max-width: 1100px) {
		.wide,
		.status-col,
		.role-col {
			display: none;
		}

		.inline {
			display: flex;
		}

		.storage {
			min-width: 110px;
		}

		.storage .bar {
			max-width: 110px;
		}

		.who {
			min-width: 200px;
		}

		table.data :is(th, td) {
			padding-inline: var(--space-2);
		}
	}
</style>
