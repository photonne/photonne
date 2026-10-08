<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { purgeNotifications } from '#lib/api/index.js';
	import {
		getNotificationStatsOptions,
		getNotificationStatsQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import ConfirmDialog from './ConfirmDialog.svelte';
	import { toastError } from './feedback.svelte.js';
	import { count, localDateTime } from './time.js';

	let { readOnly }: { readOnly: boolean } = $props();

	const queryClient = useQueryClient();
	const stats = createQuery(() => getNotificationStatsOptions());

	let confirm = $state(false);
	let purging = $state(false);

	async function purge() {
		purging = true;
		const { data, error } = await purgeNotifications();
		purging = false;
		confirm = false;
		if (error || !data) return toastError(error);
		toasts.show(m.ops_notif_purged({ count: data.deleted }));
		queryClient.invalidateQueries({ queryKey: getNotificationStatsQueryKey() });
	}
</script>

<section class="ops-card panel" aria-labelledby="notif-usage">
	<h2 id="notif-usage">{m.ops_notif_usage()}</h2>
	{#if stats.isPending}
		<p class="ops-muted">{m.ops_loading()}</p>
	{:else if stats.isError || !stats.data}
		<p class="ops-muted" role="alert">{m.ops_load_failed()}</p>
	{:else}
		<dl class="facts">
			<div>
				<dt>{m.ops_notif_total()}</dt>
				<dd>{count(stats.data.total)}</dd>
			</div>
			<div>
				<dt>{m.ops_notif_unread()}</dt>
				<dd>{count(stats.data.unread)}</dd>
			</div>
			<div>
				<dt>{m.ops_notif_oldest()}</dt>
				<dd>{stats.data.oldestAt ? localDateTime(stats.data.oldestAt) : '—'}</dd>
			</div>
		</dl>
		<p class="ops-small ops-muted">{m.ops_notif_purge_hint()}</p>
		<div>
			<button
				type="button"
				class="ops-btn danger"
				disabled={readOnly || stats.data.total === 0 || purging}
				onclick={() => (confirm = true)}>{m.ops_notif_purge()}</button
			>
		</div>
	{/if}
</section>

<ConfirmDialog
	open={confirm}
	title={m.ops_notif_purge()}
	message={m.ops_notif_purge_text()}
	confirmLabel={m.ops_notif_purge()}
	danger
	busy={purging}
	onconfirm={purge}
	onclose={() => (confirm = false)}
/>
