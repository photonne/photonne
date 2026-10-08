<script lang="ts">
	import { goto } from '$app/navigation';
	import { appHref } from '#lib/navigation/href.js';

	// /settings opens on the first section; each section has its own URL.
	goto(appHref('/settings/profile'), { replace: true });
</script>
