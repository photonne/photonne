<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { cleanupExpiredTrash } from '#lib/api/index.js';
	import {
		getTrashStatsOptions,
		getTrashStatsQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { formatBytes } from '#lib/format.js';
	import { m } from '#lib/paraglide/messages.js';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import ConfirmDialog from '#lib/components/ConfirmDialog.svelte';
	import { toastError } from './feedback.svelte.js';
	import { count } from './time.js';

	let { readOnly }: { readOnly: boolean } = $props();

	const queryClient = useQueryClient();
	const stats = createQuery(() => getTrashStatsOptions());

	let confirm = $state(false);
	let cleaning = $state(false);

	async function cleanup() {
		cleaning = true;
		const { data, error } = await cleanupExpiredTrash();
		cleaning = false;
		confirm = false;
		if (error || !data) return toastError(error);
		toasts.show(m.ops_trash_cleaned({ count: data.deleted }));
		queryClient.invalidateQueries({ queryKey: getTrashStatsQueryKey() });
	}
</script>

<section class="ops-card panel" aria-labelledby="trash-usage">
	<h2 id="trash-usage">{m.ops_trash_usage()}</h2>
	{#if stats.isPending}
		<div class="loading"><Skeleton variant="text" count={3} /></div>
	{:else if stats.isError || !stats.data}
		<p class="muted" role="alert">{m.ops_load_failed()}</p>
	{:else}
		{@const s = stats.data}
		<dl class="ops-facts">
			<div>
				<dt>{m.ops_trash_items()}</dt>
				<dd>{count(s.totalItems)}</dd>
			</div>
			<div>
				<dt>{m.ops_trash_bytes()}</dt>
				<dd>{formatBytes(s.totalBytes)}</dd>
			</div>
			<div>
				<dt>{m.ops_trash_expired()}</dt>
				<dd>{count(s.expiredItems)}</dd>
			</div>
			<div>
				<dt>{m.ops_trash_over_users()}</dt>
				<dd>{count(s.overQuotaUsers)}</dd>
			</div>
			<div>
				<dt>{m.ops_trash_over_bytes()}</dt>
				<dd>{formatBytes(s.overQuotaBytes)}</dd>
			</div>
		</dl>
		{#if s.perUser.length}
			<table class="ops-table">
				<caption class="visually-hidden">{m.ops_trash_per_user()}</caption>
				<thead>
					<tr>
						<th scope="col">{m.ops_col_user()}</th>
						<th scope="col" class="num">{m.ops_trash_items()}</th>
						<th scope="col" class="num">{m.ops_trash_bytes()}</th>
						<th scope="col" class="num">{m.ops_trash_expired()}</th>
					</tr>
				</thead>
				<tbody>
					{#each s.perUser as user (user.userId)}
						<tr class:over={user.overQuota}>
							<th scope="row">
								{user.username}
								{#if user.overQuota}<span class="chip tag danger">{m.ops_trash_over_quota()}</span
									>{/if}
							</th>
							<td class="num">{count(user.items)}</td>
							<td class="num">{formatBytes(user.bytes)}</td>
							<td class="num">{count(user.expiredItems)}</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{/if}
		<div>
			<button
				type="button"
				class="btn danger"
				disabled={readOnly || s.expiredItems === 0 || cleaning}
				onclick={() => (confirm = true)}>{m.ops_trash_cleanup({ count: s.expiredItems })}</button
			>
		</div>
	{/if}
</section>

<ConfirmDialog
	open={confirm}
	title={m.ops_trash_cleanup({ count: stats.data?.expiredItems ?? 0 })}
	message={m.ops_trash_cleanup_text()}
	confirmLabel={m.ops_trash_cleanup_short()}
	danger
	busy={cleaning}
	onconfirm={cleanup}
	onclose={() => (confirm = false)}
/>

<style>
	.loading {
		margin: 0 calc(-1 * var(--page-gutter));
	}

	tr.over th,
	tr.over td {
		color: var(--color-danger);
	}
</style>
