<script lang="ts">
	import type { Snippet } from 'svelte';
	import { chrome } from '#lib/navigation/chrome.svelte.js';

	/**
	 * What a page adds to the floating chrome while it is on screen: its own
	 * buttons (`actions`, drawn in a capsule next to the search) and, with
	 * `bleed`, the whole window (see Chrome.bleed), under the navigation
	 * too with `wide` (Chrome.wide).
	 */
	let {
		bleed = false,
		wide = false,
		actions
	}: { bleed?: boolean; wide?: boolean; actions?: Snippet } = $props();

	$effect(() => {
		chrome.bleed = bleed;
		chrome.wide = bleed && wide;
		return () => {
			chrome.bleed = false;
			chrome.wide = false;
		};
	});

	$effect(() => {
		const own = actions ?? null;
		chrome.actions = own;
		return () => {
			if (chrome.actions === own) chrome.actions = null;
		};
	});
</script>
