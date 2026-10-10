<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		removeExternalLibraryPermission,
		setExternalLibraryPermission,
		type ExternalLibraryDto
	} from '#lib/api/index.js';
	import {
		getExternalLibraryPermissionsOptions,
		getExternalLibraryPermissionsQueryKey,
		getShareableUsersOptions
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { errorText } from './errors.js';
	import { localDate } from './format.js';

	let { library, onclose }: { library: ExternalLibraryDto | null; onclose: () => void } = $props();

	const queryClient = useQueryClient();
	const id = $derived(library?.id ?? '');
	const permissions = createQuery(() => ({
		...getExternalLibraryPermissionsOptions({ path: { libraryId: id } }),
		enabled: !!library
	}));
	const users = createQuery(() => ({ ...getShareableUsersOptions(), enabled: !!library }));

	let selected = $state('');
	let busy = $state(false);

	const candidates = $derived(
		(users.data ?? []).filter((u) => !(permissions.data ?? []).some((p) => p.userId === u.id))
	);

	$effect(() => {
		if (library) selected = '';
	});

	function invalidate() {
		return queryClient.invalidateQueries({
			queryKey: getExternalLibraryPermissionsQueryKey({ path: { libraryId: id } })
		});
	}

	async function grant(event: SubmitEvent) {
		event.preventDefault();
		if (!selected || !library) return;
		busy = true;
		const user = candidates.find((u) => u.id === selected);
		const { error } = await setExternalLibraryPermission({
			path: { libraryId: id },
			body: { userId: selected, canRead: true }
		});
		busy = false;
		if (error) {
			toasts.error(errorText(error));
			return;
		}
		selected = '';
		await invalidate();
		toasts.show(m.admin_libs_access_granted({ name: user?.username ?? '', library: library.name }));
	}

	async function revoke(userId: string, username: string) {
		if (!library) return;
		busy = true;
		const { error } = await removeExternalLibraryPermission({ path: { libraryId: id, userId } });
		busy = false;
		if (error) {
			toasts.error(errorText(error));
			return;
		}
		await invalidate();
		toasts.show(m.admin_libs_access_revoked({ name: username, library: library.name }), {
			action: {
				label: m.action_undo(),
				run: async () => {
					await setExternalLibraryPermission({
						path: { libraryId: id },
						body: { userId, canRead: true }
					});
					await invalidate();
				}
			}
		});
	}
</script>

<Dialog
	open={library !== null}
	title={m.admin_libs_access_title({ name: library?.name ?? '' })}
	{onclose}
	width="520px"
>
	<div class="access">
		<p class="muted small">{m.admin_libs_access_intro()}</p>
		{#if permissions.isPending}
			<Skeleton variant="rows" count={2} />
		{:else if permissions.isError}
			<p class="field-error" role="alert">{m.error_loading()}</p>
		{:else}
			<ul class="people" aria-label={m.admin_libs_access_list()}>
				{#each permissions.data ?? [] as permission (permission.userId)}
					<li>
						<span class="who">
							<strong>{permission.username}</strong>
							<span class="muted small">{permission.email}</span>
						</span>
						<span class="muted small"
							>{m.admin_libs_access_since({ date: localDate(permission.grantedAt) })}</span
						>
						<button
							type="button"
							class="icon-btn danger"
							aria-label={m.admin_libs_access_revoke({ name: permission.username })}
							title={m.admin_libs_access_revoke({ name: permission.username })}
							disabled={busy}
							onclick={() => revoke(permission.userId, permission.username)}
						>
							<Icon name="close" size={18} />
						</button>
					</li>
				{:else}
					<li class="muted small empty">{m.admin_libs_access_none()}</li>
				{/each}
			</ul>
		{/if}

		<form class="grant" onsubmit={grant}>
			<label class="field">
				<span>{m.admin_libs_access_add()}</span>
				<select bind:value={selected} disabled={!candidates.length}>
					<option value="" disabled
						>{candidates.length
							? m.admin_libs_access_pick()
							: m.admin_libs_access_everyone()}</option
					>
					{#each candidates as user (user.id)}
						<option value={user.id}>{user.username} ({user.email})</option>
					{/each}
				</select>
			</label>
			<button type="submit" class="btn primary" disabled={!selected || busy}
				>{m.admin_libs_access_grant()}</button
			>
		</form>
	</div>
	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.admin_core_close()}</button>
	{/snippet}
</Dialog>

<style>
	.access {
		display: grid;
		gap: var(--space-3);
	}

	p {
		margin: 0;
	}

	.people {
		display: grid;
		margin: 0;
		padding: 0;
		list-style: none;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
	}

	.people li {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-3);
	}

	.people li + li {
		border-top: 1px solid var(--color-border);
	}

	.who {
		flex: 1;
		display: grid;
		min-width: 0;
	}

	.empty {
		padding: var(--space-3);
	}

	.grant {
		display: flex;
		align-items: flex-end;
		gap: var(--space-2);
	}

	.grant .field {
		flex: 1;
	}
</style>
