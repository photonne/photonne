<script lang="ts">
	import { createQuery } from '@tanstack/svelte-query';
	import { getServerInfoOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { icons } from './icons.js';

	const info = createQuery(() => getServerInfoOptions());
	const cores = $derived(info.data?.processorCount ?? 0);
</script>

<section class="ops-card panel" aria-labelledby="perf-server">
	<h2 id="perf-server">{m.ops_perf_server()}</h2>
	{#if cores > 0}
		<p>{m.ops_perf_cores({ count: cores })}</p>
		<dl class="ops-facts">
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
		<div class="loading"><Skeleton variant="text" count={3} /></div>
	{/if}
	<p class="small muted">{m.ops_perf_hint()}</p>
	<p class="notice warning">
		<Icon path={icons.warning} size={18} />
		{m.ops_perf_restart()}
	</p>
</section>

<style>
	.loading {
		margin: 0 calc(-1 * var(--page-gutter));
	}
</style>
