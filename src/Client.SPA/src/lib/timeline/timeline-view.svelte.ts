import { createQuery } from '@tanstack/svelte-query';
import { getTimelineYearsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { monthTitle } from '#lib/format.js';
import { m } from '#lib/paraglide/messages.js';
import type { GridSection } from './grid-model.js';
import { toGridAsset, type GridAsset } from './types.js';
import { parseZoom, stepZoom, yearSampleSize, type ZoomLevel } from './zoom.js';

const STORAGE_KEY = 'photonne.timeline.zoom';

// What PhotoGrid adds around the photos (side padding and the scrubber), to
// size the year samples from the grid's outer width.
const GRID_CHROME = 24 + 56;

/**
 * How the timeline is shown: the zoom level (remembered) and, at the year
 * level, the per-year samples that replace the months. Created by the page,
 * during component setup (it owns a query).
 */
export class TimelineView {
	zoom = $state<ZoomLevel>(readSaved());
	/** The grid's outer width, bound by the page: the year samples depend on it. */
	width = $state(0);

	#years = createQuery(() => ({
		...getTimelineYearsOptions({
			query: { sample: yearSampleSize(Math.max(0, this.width - GRID_CHROME)) }
		}),
		enabled: this.zoom === 'year' && this.width > 0
	}));
	#months: () => GridSection<GridAsset>[];

	constructor(months: () => GridSection<GridAsset>[]) {
		this.#months = months;
	}

	/** Showing the year samples (they arrive a moment after choosing the level). */
	yearView = $derived(this.zoom === 'year' && !!this.#years.data);

	#yearCounts = $derived(
		Object.fromEntries((this.#years.data ?? []).map((year) => [String(year.year), year.count]))
	);

	sections: GridSection<GridAsset>[] = $derived.by(() => {
		if (!this.yearView) return this.#months();
		return (this.#years.data ?? [])
			.filter((year) => year.items.length > 0)
			.map((year) => ({
				key: String(year.year),
				count: year.items.length,
				items: year.items.map(toGridAsset)
			}));
	});

	sectionTitle = (key: string) => (key.length === 4 ? key : monthTitle(key));

	sectionSubtitle = (key: string) => {
		const count = this.#yearCounts[key];
		return count === undefined ? '' : m.timeline_year_items({ count });
	};

	setZoom(level: ZoomLevel) {
		this.zoom = level;
		try {
			localStorage.setItem(STORAGE_KEY, level);
		} catch {
			// Storage may be unavailable (private mode); the level lasts the visit.
		}
	}

	step(direction: 1 | -1) {
		this.setZoom(stepZoom(this.zoom, direction));
	}
}

function readSaved() {
	try {
		return parseZoom(localStorage.getItem(STORAGE_KEY));
	} catch {
		return parseZoom(null);
	}
}
