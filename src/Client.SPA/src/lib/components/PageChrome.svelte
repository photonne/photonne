<script lang="ts">
	import type { Snippet } from 'svelte';
	import { chrome } from '#lib/navigation/chrome.svelte.js';

	/**
	 * What a page adds to the floating chrome while it is on screen: its own
	 * buttons (`actions`, drawn in a capsule next to the search) and, with
	 * `bleed`, the whole window (see Chrome.bleed).
	 */
	let { bleed = false, actions }: { bleed?: boolean; actions?: Snippet } = $props();

	$effect(() => {
		chrome.bleed = bleed;
		return () => (chrome.bleed = false);
	});

	$effect(() => {
		const own = actions ?? null;
		chrome.actions = own;
		return () => {
			if (chrome.actions === own) chrome.actions = null;
		};
	});
</script>
