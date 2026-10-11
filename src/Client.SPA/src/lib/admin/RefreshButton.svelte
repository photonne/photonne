<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';

	/**
	 * The one refresh button of the admin pages: same look, label and place
	 * (last in the page's action bar) everywhere.
	 */
	let { onclick, busy = false }: { onclick: () => unknown; busy?: boolean } = $props();
</script>

<button type="button" class="btn" disabled={busy} aria-busy={busy} {onclick}>
	<Icon name="refresh" size={18} />
	{m.admin_core_refresh()}
</button>
