<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { saveSetting } from '#lib/api/index.js';
	import {
		getSettingOptions,
		getSettingQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { toastError } from './feedback.svelte.js';

	let { readOnly }: { readOnly: boolean } = $props();

	const KEY = 'NightlyTaskSettings.LastRunDate';
	const queryClient = useQueryClient();
	const lastRun = createQuery(() => getSettingOptions({ query: { key: KEY } }));
	let resetting = $state(false);

	/** The scheduler runs once a day; forgetting the last run lets it run again tonight. */
	async function reset() {
		resetting = true;
		const { error } = await saveSetting({ body: { key: KEY, value: '' } });
		resetting = false;
		if (error) return toastError(error);
		toasts.show(m.ops_nightly_reset_done());
		queryClient.invalidateQueries({ queryKey: getSettingQueryKey({ query: { key: KEY } }) });
	}
</script>

<section class="ops-card panel" aria-labelledby="nightly-last">
	<h2 id="nightly-last">{m.ops_nightly_last()}</h2>
	{#if lastRun.isPending}
		<p class="ops-muted">{m.ops_loading()}</p>
	{:else if lastRun.data?.value}
		<p>{m.ops_nightly_last_value({ date: lastRun.data.value })}</p>
		<p class="ops-small ops-muted">{m.ops_nightly_last_hint()}</p>
	{:else}
		<p class="ops-muted">{m.ops_nightly_never()}</p>
	{/if}
	<div>
		<button
			type="button"
			class="ops-btn"
			disabled={readOnly || !lastRun.data?.value || resetting}
			onclick={reset}>{m.ops_nightly_reset()}</button
		>
	</div>
</section>
