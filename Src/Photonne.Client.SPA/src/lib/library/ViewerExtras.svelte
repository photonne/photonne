<script lang="ts">
	import { mount, unmount } from 'svelte';
	import ViewerExtraButtons, { type ViewerExtraAction } from './ViewerExtraButtons.svelte';

	/**
	 * Adds page-specific buttons (unarchive, restore, delete for good) to the
	 * top bar of the photo viewer that CollectionView opens.
	 *
	 * Stopgap: AssetViewer only takes trash/archive/album callbacks and has no
	 * slot for a page's own actions, so the buttons are mounted into its
	 * action bar. They join its focus order and keyboard flow like its own.
	 * When AssetViewer grows an `actions` snippet (and CollectionView passes
	 * one through), this component goes away.
	 */
	let { openId, actions }: { openId: string | null; actions: ViewerExtraAction[] } = $props();

	const mounted = $state<{ actions: ViewerExtraAction[] }>({ actions: [] });

	$effect.pre(() => {
		mounted.actions = actions;
	});

	const open = $derived(openId !== null);

	$effect(() => {
		if (!open) return;
		const bar = document.querySelector<HTMLElement>('.viewer[role="dialog"] .bar .actions');
		if (!bar) return;
		// Before the download link, next to the viewer's own trash/archive.
		const anchor = bar.querySelector('a[download]') ?? undefined;
		const component = mount(ViewerExtraButtons, { target: bar, anchor, props: mounted });
		return () => void unmount(component);
	});
</script>
