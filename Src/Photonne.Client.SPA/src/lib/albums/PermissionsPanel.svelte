<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		removeAlbumPermission,
		removeFolderPermission,
		setAlbumPermission,
		setFolderPermission,
		type AlbumPermissionDto
	} from '#lib/api/index.js';
	import {
		getAlbumPermissionsOptions,
		getAlbumPermissionsQueryKey,
		getFolderPermissionsOptions,
		getFolderPermissionsQueryKey,
		getShareableUsersOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from './icons.js';
	import {
		accessLevels,
		grantsFor,
		levelOf,
		type AccessLevel,
		type Grants
	} from './permissions.js';

	interface Props {
		kind: 'album' | 'folder';
		targetId: string;
		/** After any change, so the host refreshes its "shared with" counts. */
		onchanged: () => void;
	}

	let { kind, targetId, onchanged }: Props = $props();

	const queryClient = useQueryClient();

	// Albums and folders expose the same permission API under their own routes.
	const albumPermissions = createQuery(() => ({
		...getAlbumPermissionsOptions({ path: { albumId: targetId } }),
		enabled: kind === 'album'
	}));
	const folderPermissions = createQuery(() => ({
		...getFolderPermissionsOptions({ path: { folderId: targetId } }),
		enabled: kind === 'folder'
	}));
	const permissions = $derived(kind === 'album' ? albumPermissions : folderPermissions);

	const api = $derived(
		kind === 'album'
			? {
					key: getAlbumPermissionsQueryKey({ path: { albumId: targetId } }),
					set: (body: Grants & { userId: string }) =>
						setAlbumPermission({ path: { albumId: targetId }, body }),
					remove: (userId: string) => removeAlbumPermission({ path: { albumId: targetId, userId } })
				}
			: {
					key: getFolderPermissionsQueryKey({ path: { folderId: targetId } }),
					set: (body: Grants & { userId: string }) =>
						setFolderPermission({ path: { folderId: targetId }, body }),
					remove: (userId: string) =>
						removeFolderPermission({ path: { folderId: targetId, userId } })
				}
	);

	const users = createQuery(() => getShareableUsersOptions());

	const levelLabels: Record<AccessLevel, () => string> = {
		view: m.albums_level_view,
		contribute: m.albums_level_contribute,
		edit: m.albums_level_edit,
		manage: m.albums_level_manage
	};

	// The owner has no row (their access can't change); the viewer's own row
	// (a shared folder's creator gets one) isn't theirs to edit here.
	const granted = $derived(
		(permissions.data ?? []).filter((p: AlbumPermissionDto) => p.userId !== session.user?.id)
	);
	const available = $derived(
		(users.data ?? []).filter(
			(user) => user.id !== session.user?.id && !granted.some((p) => p.userId === user.id)
		)
	);

	let newUser = $state('');
	let newLevel = $state<AccessLevel>('view');
	let busy = $state(false);

	async function save(userId: string, grants: Grants) {
		busy = true;
		const { error } = await api.set({ userId, ...grants });
		busy = false;
		if (error) {
			toasts.error(m.action_failed());
			return false;
		}
		await queryClient.invalidateQueries({ queryKey: api.key });
		onchanged();
		return true;
	}

	async function add(event: SubmitEvent) {
		event.preventDefault();
		const user = available.find((u) => u.id === newUser);
		if (!user) return;
		if (await save(user.id, grantsFor(newLevel))) {
			toasts.show(m.albums_shared_with_user({ name: user.username }));
			newUser = '';
			newLevel = 'view';
		}
	}

	async function remove(permission: AlbumPermissionDto) {
		busy = true;
		const { error } = await api.remove(permission.userId);
		busy = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		await queryClient.invalidateQueries({ queryKey: api.key });
		onchanged();
		const grants: Grants = {
			canRead: permission.canRead,
			canWrite: permission.canWrite,
			canDelete: permission.canDelete,
			canManagePermissions: permission.canManagePermissions
		};
		toasts.show(m.albums_unshared_with_user({ name: permission.username }), {
			action: { label: m.action_undo(), run: () => void save(permission.userId, grants) }
		});
	}
</script>

<div class="panel">
	{#if permissions.isPending}
		<p class="note" role="status">{m.session_restoring()}</p>
	{:else if permissions.isError}
		<p class="note" role="alert">{m.error_loading()}</p>
	{:else if granted.length === 0}
		<p class="note">{m.albums_people_none()}</p>
	{:else}
		<ul class="people" aria-label={m.albums_people_list()}>
			{#each granted as permission (permission.userId)}
				{@const level = levelOf(permission)}
				<li>
					<span class="who">
						<span class="name">{permission.username}</span>
						<span class="email">{permission.email}</span>
					</span>
					<select
						aria-label={m.albums_level_for({ name: permission.username })}
						value={level}
						disabled={busy}
						onchange={(event) =>
							save(permission.userId, grantsFor(event.currentTarget.value as AccessLevel))}
					>
						{#if level === 'custom'}
							<option value="custom" disabled>{m.albums_level_custom()}</option>
						{/if}
						{#each accessLevels as option (option)}
							<option value={option}>{levelLabels[option]()}</option>
						{/each}
					</select>
					<button
						type="button"
						class="icon"
						disabled={busy}
						aria-label={m.albums_unshare_user({ name: permission.username })}
						title={m.albums_unshare_user({ name: permission.username })}
						onclick={() => remove(permission)}
					>
						<Icon path={icons.personRemove} size={18} />
					</button>
				</li>
			{/each}
		</ul>
	{/if}

	<form class="add" onsubmit={add}>
		<label class="grow">
			<span class="visually-hidden">{m.albums_people_add()}</span>
			<select bind:value={newUser} disabled={available.length === 0}>
				<option value="" disabled>
					{users.isSuccess && available.length === 0
						? m.albums_people_no_more()
						: m.albums_people_add()}
				</option>
				{#each available as user (user.id)}
					<option value={user.id}>{user.username} ({user.email})</option>
				{/each}
			</select>
		</label>
		<label>
			<span class="visually-hidden">{m.albums_level()}</span>
			<select bind:value={newLevel}>
				{#each accessLevels as option (option)}
					<option value={option}>{levelLabels[option]()}</option>
				{/each}
			</select>
		</label>
		<button type="submit" class="primary" disabled={!newUser || busy}>
			<Icon path={icons.personAdd} size={18} />
			{m.albums_share_submit()}
		</button>
	</form>
	<p class="hint">{m.albums_level_hint()}</p>
</div>

<style>
	.panel {
		display: grid;
		gap: var(--space-3);
	}

	.note,
	.hint {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.people {
		display: grid;
		gap: var(--space-1);
		margin: 0;
		padding: 0;
		list-style: none;
		max-height: 260px;
		overflow-y: auto;
	}

	.people li {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1) 0;
	}

	.who {
		flex: 1;
		display: grid;
		min-width: 0;
	}

	.name {
		font-weight: 600;
	}

	.email {
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
		overflow: hidden;
		text-overflow: ellipsis;
	}

	select {
		padding: var(--space-1) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.add {
		display: flex;
		gap: var(--space-2);
		padding-top: var(--space-3);
		border-top: 1px solid var(--color-border);
	}

	.grow {
		flex: 1;
		min-width: 0;
	}

	.grow select {
		width: 100%;
	}

	.primary {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		padding: var(--space-1) var(--space-3);
		border: 0;
		border-radius: var(--radius-sm);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
		cursor: pointer;
	}

	.primary:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.icon {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.icon:hover {
		background: var(--color-surface);
	}
</style>
