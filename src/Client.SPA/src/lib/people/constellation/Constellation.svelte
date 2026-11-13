<script lang="ts">
	import { onMount, untrack } from 'svelte';
	import { goto } from '$app/navigation';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import {
		categoryName,
		RELATIONSHIP_CATEGORIES,
		relationshipCategory,
		type RelationshipCategory
	} from '../relationship.js';
	import {
		brightness,
		buildSky,
		laneAt,
		photosUntil,
		yearStep,
		type SkyPerson,
		type Star
	} from './sky.js';

	interface Props {
		people: readonly SkyPerson[];
		/** The present; a parameter so tests and screenshots are stable. */
		today?: Date;
	}

	let { people, today = new Date() }: Props = $props();

	type Key = RelationshipCategory | 'none';
	const keyOf = (category: RelationshipCategory | null): Key => category ?? 'none';

	// The sky is always night, whatever the app's theme: its own colors.
	const COLORS: Record<Key | 'you', string> = {
		you: '#ffffff',
		partner: '#ff8fa3',
		children: '#ff9d5c',
		family: '#f2d16b',
		friends: '#6fc3ff',
		pet: '#7fe0b0',
		work: '#b79bff',
		other: '#c8cede',
		none: '#8d96b8'
	};
	const MUTED = '#8d96b8';
	/** Seconds a whole life takes to play. */
	const SPEEDS = { slow: 45, normal: 20, fast: 9 } as const;
	const MIN_LANE = 18;
	const PAD_TOP = 26;
	const PAD_BOTTOM = 46;

	// Categories in use, in the legend's order; pressing one hides its stars.
	const present = $derived.by(() => {
		const used = new Set(people.map((p) => keyOf(relationshipCategory(p.relationshipCategory))));
		return ([...RELATIONSHIP_CATEGORIES, 'none'] as Key[]).filter((key) => used.has(key));
	});
	let hidden = $state<Key[]>([]);
	const sky = $derived(
		buildSky(
			people.filter((p) => !hidden.includes(keyOf(relationshipCategory(p.relationshipCategory)))),
			today
		)
	);
	const unclassified = $derived(people.filter((p) => !p.relationshipCategory).length);

	const reduceMotion =
		typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	// The playhead, in decimal years. Still (and at the end) with reduced motion.
	let now = $state(untrack(() => (reduceMotion ? sky.to : sky.from)));
	let playing = $state(!reduceMotion);
	let follow = $state(false);
	let speed = $state<keyof typeof SPEEDS>('normal');
	let hover = $state<{ star: Star; x: number; y: number } | null>(null);

	const started = $derived(sky.stars.filter((star) => star.start <= now));
	const stopped = $derived(started.filter((star) => star.status === 'fixed' && now >= star.end));
	const atEnd = $derived(now >= sky.to);

	let wrap = $state<HTMLDivElement>();
	let canvas = $state<HTMLCanvasElement>();
	let width = $state(0);
	let viewHeight = $state(0);
	const lanes = $derived(sky.bottom - sky.top + 1.6);
	const height = $derived(Math.max(viewHeight, lanes * MIN_LANE + PAD_TOP + PAD_BOTTOM));

	// 0 = the whole life fits; 1 = zoomed in, following the present.
	let mix = 0;
	let labelRoom = 80;

	function x(t: number) {
		const full = 20 + ((t - sky.from) / (sky.to - sky.from)) * (width - 20 - labelRoom);
		const following = width * 0.7 + (t - now) * Math.max(width / 11, 58);
		return full + (following - full) * mix;
	}

	function timeAt(px: number) {
		const a = x(0);
		return (px - a) / (x(1) - a);
	}

	function y(lane: number) {
		return PAD_TOP + (lane - sky.top + 0.8) * ((height - PAD_TOP - PAD_BOTTOM) / lanes);
	}

	const rgba = (hex: string, alpha: number) => {
		const n = parseInt(hex.slice(1), 16);
		return `rgba(${n >> 16},${(n >> 8) & 255},${n & 255},${alpha})`;
	};

	// A fixed scatter of faint background stars, the same on every frame.
	const dust = Array.from({ length: 260 }, (_, i) => {
		const r = (k: number) => {
			const v = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
			return v - Math.floor(v);
		};
		return { x: r(1), y: r(2), r: r(3) * 0.9 + 0.2, a: r(4) * 0.35 + 0.08 };
	});

	function draw(ms: number) {
		const ctx = canvas?.getContext('2d');
		if (!ctx || !canvas || width === 0) return;
		const dpr = Math.min(window.devicePixelRatio || 1, 2);
		if (canvas.width !== Math.round(width * dpr) || canvas.height !== Math.round(height * dpr)) {
			canvas.width = Math.round(width * dpr);
			canvas.height = Math.round(height * dpr);
		}
		const font = getComputedStyle(canvas).fontFamily;
		ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
		ctx.clearRect(0, 0, width, height);
		for (const speck of dust) {
			ctx.fillStyle = `rgba(200,210,255,${speck.a})`;
			ctx.beginPath();
			ctx.arc(speck.x * width, speck.y * height, speck.r, 0, 6.3);
			ctx.fill();
		}

		ctx.font = `500 12px ${font}`;
		labelRoom = Math.min(
			170,
			28 + Math.max(24, ...sky.stars.map((star) => ctx.measureText(star.name).width))
		);

		// Year lines.
		const bottom = height - PAD_BOTTOM + 16;
		const step = yearStep(x(1) - x(0));
		ctx.font = `11px ${font}`;
		ctx.textAlign = 'center';
		ctx.textBaseline = 'alphabetic';
		ctx.lineWidth = 1;
		for (let year = Math.ceil(sky.from / step) * step; year <= sky.to; year += step) {
			const px = x(year);
			if (px < 0 || px > width) continue;
			ctx.strokeStyle = 'rgba(160,175,230,.08)';
			ctx.beginPath();
			ctx.moveTo(px, 14);
			ctx.lineTo(px, bottom);
			ctx.stroke();
			ctx.fillStyle = year > now ? rgba(MUTED, 0.35) : MUTED;
			ctx.fillText(String(year), px, height - 12);
		}

		// The present.
		const front = x(now);
		const glow = ctx.createLinearGradient(0, 14, 0, bottom);
		glow.addColorStop(0, 'rgba(255,255,255,0)');
		glow.addColorStop(0.5, 'rgba(255,255,255,.22)');
		glow.addColorStop(1, 'rgba(255,255,255,0)');
		ctx.strokeStyle = glow;
		ctx.beginPath();
		ctx.moveTo(front, 14);
		ctx.lineTo(front, bottom);
		ctx.stroke();

		const twinkle = reduceMotion ? 0 : ms / 1000;
		ctx.lineJoin = 'round';
		ctx.textAlign = 'left';
		ctx.textBaseline = 'middle';

		const trail = (
			from: number,
			to: number,
			lane: (t: number) => number,
			color: string,
			fixed: boolean,
			lineWidth: number
		) => {
			ctx.beginPath();
			for (let t = from; ; t += 0.08) {
				const at = Math.min(t, to);
				if (t === from) ctx.moveTo(x(at), y(lane(at)));
				else ctx.lineTo(x(at), y(lane(at)));
				if (at >= to) break;
			}
			if (fixed) ctx.strokeStyle = rgba(color, 0.3);
			else {
				const fade = ctx.createLinearGradient(x(from), 0, Math.max(x(to), x(from) + 1), 0);
				fade.addColorStop(0, rgba(color, 0.1));
				fade.addColorStop(1, rgba(color, 0.9));
				ctx.strokeStyle = fade;
			}
			ctx.lineWidth = lineWidth;
			ctx.stroke();
		};

		const head = (px: number, py: number, color: string, radius: number, light: number) => {
			const halo = ctx.createRadialGradient(px, py, 0, px, py, radius);
			halo.addColorStop(0, rgba(color, 0.75 * light));
			halo.addColorStop(0.35, rgba(color, 0.22 * light));
			halo.addColorStop(1, rgba(color, 0));
			ctx.fillStyle = halo;
			ctx.beginPath();
			ctx.arc(px, py, radius, 0, 6.3);
			ctx.fill();
			ctx.fillStyle = rgba('#ffffff', 0.4 + 0.6 * light);
			ctx.beginPath();
			ctx.arc(px, py, radius > 14 ? 3.2 : 2.2, 0, 6.3);
			ctx.fill();
		};

		for (const [index, star] of sky.stars.entries()) {
			if (now < star.start) continue;
			const color = COLORS[keyOf(star.category)];
			const fixed = star.status === 'fixed' && now >= star.end;
			const until = Math.min(now, star.until);
			trail(star.start, until, (t) => laneAt(star, t), color, fixed, 1.4);

			const px = x(until);
			const py = y(laneAt(star, until));
			if (fixed) {
				const since = now - star.end;
				if (since < 1.5) {
					// The moment it stays behind.
					ctx.strokeStyle = rgba(color, 0.6 * (1 - since / 1.5));
					ctx.lineWidth = 1;
					ctx.beginPath();
					ctx.arc(px, py, 4 + since * 14, 0, 6.3);
					ctx.stroke();
				}
				ctx.strokeStyle = rgba(color, 0.55);
				ctx.lineWidth = 1;
				ctx.beginPath();
				ctx.moveTo(px - 6, py);
				ctx.lineTo(px + 6, py);
				ctx.moveTo(px, py - 6);
				ctx.lineTo(px, py + 6);
				ctx.stroke();
				ctx.fillStyle = rgba(color, 0.9);
				ctx.beginPath();
				ctx.arc(px, py, 1.8, 0, 6.3);
				ctx.fill();
				ctx.fillStyle = rgba(color, 0.6);
				ctx.font = `11px ${font}`;
				ctx.fillText(
					width < 560 ? star.name : `${star.name} · ${Math.floor(star.end)}`,
					px + 10,
					py
				);
			} else {
				const light = brightness(star, now);
				head(px, py, color, 11 * (1 + 0.12 * Math.sin(twinkle * 2.1 + index * 2.4)), light);
				ctx.fillStyle = rgba(color, 0.35 + 0.6 * light);
				ctx.font = `500 12px ${font}`;
				ctx.fillText(star.name, px + 12, py);
			}
		}

		// The main line: the user, from the first photo to the present.
		trail(sky.from, now, () => 0, COLORS.you, false, 2.4);
		head(front, y(0), COLORS.you, 18 * (1 + 0.12 * Math.sin(twinkle * 2.1)), 1);
		ctx.fillStyle = COLORS.you;
		ctx.font = `600 13px ${font}`;
		ctx.fillText(m.people_constellation_you(), front + 12, y(0));
	}

	onMount(() => {
		const observer = new ResizeObserver(() => {
			width = wrap?.clientWidth ?? 0;
			viewHeight = wrap?.clientHeight ?? 0;
		});
		if (wrap) observer.observe(wrap);

		let frame = 0;
		let last = performance.now();
		const tick = (ms: number) => {
			const dt = Math.min(0.05, (ms - last) / 1000);
			last = ms;
			if (playing) {
				const next = now + (dt * (sky.to - sky.from)) / SPEEDS[speed];
				if (next >= sky.to) playing = false;
				now = Math.min(next, sky.to);
			}
			mix += ((follow ? 1 : 0) - mix) * (reduceMotion ? 1 : Math.min(1, dt * 5));
			draw(ms);
			frame = requestAnimationFrame(tick);
		};
		frame = requestAnimationFrame(tick);
		return () => {
			observer.disconnect();
			cancelAnimationFrame(frame);
		};
	});

	function toggle() {
		if (!playing && atEnd) now = sky.from;
		playing = !playing;
	}

	function toggleCategory(key: Key) {
		hidden = hidden.includes(key) ? hidden.filter((k) => k !== key) : [...hidden, key];
		hover = null;
	}

	function starAt(px: number, py: number): Star | null {
		const t = timeAt(px);
		let best: { star: Star; distance: number } | null = null;
		for (const star of sky.stars) {
			const until = Math.min(now, star.until);
			if (now < star.start || t < star.start - 0.3) continue;
			// Past its head, the name is the target.
			const reach = until + (labelRoom + 40) / (x(1) - x(0));
			if (t > reach) continue;
			const distance = Math.abs(y(laneAt(star, Math.min(t, until))) - py);
			if (distance < 9 && (!best || distance < best.distance)) best = { star, distance };
		}
		return best?.star ?? null;
	}

	function onpointer(event: PointerEvent) {
		const box = canvas!.getBoundingClientRect();
		const px = event.clientX - box.left;
		const py = event.clientY - box.top;
		const star = starAt(px, py);
		hover = star ? { star, x: px, y: py } : null;
	}

	function relation(star: Star) {
		const name = categoryName(star.category);
		return star.label ? `${name} · ${star.label}` : name;
	}

	function summary(star: Star) {
		const values = { name: star.name, relation: relation(star), from: Math.floor(star.start) };
		return star.status === 'fixed'
			? m.people_constellation_star_fixed({ ...values, to: Math.floor(star.end) })
			: m.people_constellation_star_alive(values);
	}
</script>

<div class="constellation">
	<div class="status" aria-live="off">
		<span class="year">{Math.floor(now)}</span>
		<span class="count">
			{m.people_constellation_count({
				alive: started.length - stopped.length,
				fixed: stopped.length
			})}
		</span>
	</div>

	<div class="sky" bind:this={wrap}>
		<div class="stage" style:height="{height}px">
			<canvas
				bind:this={canvas}
				class:link={hover !== null}
				aria-hidden="true"
				onpointermove={onpointer}
				onpointerdown={onpointer}
				onpointerleave={() => (hover = null)}
				onclick={() => {
					if (hover) goto(appHref(`/people/${hover.star.id}`));
				}}
			></canvas>
			{#if hover}
				{@const star = hover.star}
				<div
					class="tip"
					style:left="{Math.min(hover.x + 14, Math.max(8, width - 236))}px"
					style:top="{Math.max(8, Math.min(hover.y + 14, height - 124))}px"
				>
					<strong>{star.name}</strong>
					<span>{relation(star)}</span>
					<span>{m.people_constellation_first({ year: Math.floor(star.start) })}</span>
					<span>
						{star.status === 'fixed' && now >= star.end
							? m.people_constellation_last({ year: Math.floor(star.end) })
							: m.people_constellation_ongoing()}
					</span>
					<span>{m.people_constellation_photos({ count: photosUntil(star, now) })}</span>
				</div>
			{/if}
		</div>
		<ul class="visually-hidden" aria-label={m.people_constellation_list()}>
			{#each sky.stars as star (star.id)}
				<li>{summary(star)}</li>
			{/each}
		</ul>
	</div>

	<div class="controls">
		<button type="button" class="btn primary play" onclick={toggle}>
			{playing
				? m.people_constellation_pause()
				: atEnd
					? m.people_constellation_restart()
					: m.people_constellation_play()}
		</button>
		<input
			type="range"
			class="scrub"
			min={sky.from}
			max={sky.to}
			step="0.05"
			value={now}
			aria-label={m.people_constellation_year()}
			aria-valuetext={String(Math.floor(now))}
			oninput={(event) => {
				now = Number(event.currentTarget.value);
				playing = false;
			}}
		/>
		<label class="speed">
			<span class="visually-hidden">{m.people_constellation_speed()}</span>
			<select bind:value={speed}>
				<option value="slow">{m.people_constellation_speed_slow()}</option>
				<option value="normal">{m.people_constellation_speed_normal()}</option>
				<option value="fast">{m.people_constellation_speed_fast()}</option>
			</select>
		</label>
		<div class="segmented" role="group" aria-label={m.people_constellation_view()}>
			<button type="button" aria-pressed={!follow} onclick={() => (follow = false)}>
				{m.people_constellation_view_all()}
			</button>
			<button type="button" aria-pressed={follow} onclick={() => (follow = true)}>
				{m.people_constellation_view_follow()}
			</button>
		</div>
	</div>

	<div class="legend" role="group" aria-label={m.people_constellation_legend()}>
		{#each present as key (key)}
			<button
				type="button"
				class="chip"
				aria-pressed={!hidden.includes(key)}
				onclick={() => toggleCategory(key)}
			>
				<span class="dot" style:background={COLORS[key]}></span>
				{categoryName(key === 'none' ? null : key)}
			</button>
		{/each}
		{#if unclassified > 0}
			<span class="note">{m.people_constellation_unclassified({ count: unclassified })}</span>
		{/if}
	</div>
</div>

<style>
	.constellation {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		height: 100%;
		min-height: 0;
		padding: 0 var(--page-gutter) var(--space-4);
	}

	.status {
		display: flex;
		align-items: baseline;
		gap: var(--space-3);
	}

	.year {
		font-size: 2rem;
		font-weight: 600;
		line-height: 1;
		font-variant-numeric: tabular-nums;
	}

	.count {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		font-variant-numeric: tabular-nums;
	}

	/* Night whatever the theme. Scrolls when there are more lanes than room. */
	.sky {
		position: relative;
		flex: 1;
		min-height: 320px;
		overflow: hidden auto;
		border-radius: var(--radius-md);
		background: radial-gradient(120% 90% at 70% 40%, #0d1430, #070b1a 70%);
	}

	.stage {
		position: relative;
	}

	canvas {
		display: block;
		width: 100%;
		height: 100%;
		touch-action: pan-y;
	}

	canvas.link {
		cursor: pointer;
	}

	.tip {
		position: absolute;
		display: flex;
		flex-direction: column;
		max-width: 228px;
		padding: var(--space-2) var(--space-3);
		border: 1px solid rgb(160 175 230 / 0.16);
		border-radius: var(--radius-sm);
		background: rgb(18 26 56 / 0.86);
		backdrop-filter: blur(8px);
		color: #8d96b8;
		font-size: var(--font-size-xs);
		pointer-events: none;
	}

	.tip strong {
		color: #e8ebf7;
		font-size: var(--font-size-sm);
	}

	.controls {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	.play {
		min-width: 7.5rem;
		justify-content: center;
	}

	.scrub {
		flex: 1 1 220px;
		min-width: 160px;
		accent-color: var(--color-primary-fill);
	}

	.legend {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.chip[aria-pressed='false'] {
		opacity: 0.55;
	}

	.dot {
		width: 9px;
		height: 9px;
		border: 1px solid var(--color-border-strong);
		border-radius: 50%;
	}

	.note {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}
</style>
