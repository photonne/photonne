<script lang="ts" module>
	// Material Icons paths (Apache 2.0) on a 24×24 grid; livePhoto is a dot in
	// a ring, drawn with the even-odd rule.
	const paths = {
		check: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
		play: 'M8 5v14l11-7z',
		favorite:
			'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
		livePhoto:
			'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zm0-6a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 2a7 7 0 1 1 0 14 7 7 0 0 1 0-14z',
		close:
			'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z'
	} as const;

	export type IconName = keyof typeof paths;
</script>

<script lang="ts">
	let { name, size = 20 }: { name: IconName; size?: number } = $props();
</script>

<svg
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill="currentColor"
	aria-hidden="true"
	focusable="false"
>
	<path d={paths[name]} fill-rule="evenodd" />
</svg>
