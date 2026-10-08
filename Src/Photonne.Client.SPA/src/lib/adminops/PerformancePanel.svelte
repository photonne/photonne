<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getServerInfoOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from './icons.js';

	const info = createQuery(() => getServerInfoOptions());
	const cores = $derived(info.data?.processorCount ?? 0);
</script>

<section class="ops-card panel" aria-labelledby="perf-server">
	<h2 id="perf-server">{m.ops_perf_server()}</h2>
	{#if cores > 0}
		<p>{m.ops_perf_cores({ count: cores })}</p>
		<dl class="facts">
			<div>
				<dt>{m.ops_perf_recommended_io()}</dt>
				<dd>{Math.max(2, Math.floor(cores / 2))}</dd>
			</div>
			<div>
				<dt>{m.ops_perf_recommended_ml()}</dt>
				<dd>{Math.max(1, Math.floor(cores / 4))}</dd>
			</div>
		</dl>
	{:else if info.isPending}
		<p class="ops-muted">{m.ops_loading()}</p>
	{/if}
	<p class="ops-small ops-muted">{m.ops_perf_hint()}</p>
	<p class="ops-alert warn">
		<Icon path={icons.warning} size={18} />
		{m.ops_perf_restart()}
	</p>
</section>
