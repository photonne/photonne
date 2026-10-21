import { createQuery } from '@tanstack/svelte-query';
import { getTimelineYearsOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { monthTitle } from '#lib/format.js';
import { m } from '#lib/paraglide/messages.js';
import type { GridSection } from './grid-model.js';
import { toGridAsset, type GridAsset } from './types.js';
import { SizePreference } from './size-preference.svelte.js';
import {
	arrangeItems,
	yearInRange,
	type DateRange,
	type TimelineFilter,
	type TimelineOrder
} from './timeline-filter.js';
import { migrateZoomLevel, parseGrouping, yearSampleSize, type Grouping } from './zoom.js';

const SIZE_KEY = 'photonne.timeline.size';
const GROUPING_KEY = 'photonne.timeline.grouping';
const ORDER_KEY = 'photonne.timeline.order';
/** Where the timeline kept a level that set both size and grouping. */
const LEGACY_ZOOM_KEY = 'photonne.timeline.zoom';

// What PhotoGrid adds around the photos (side padding and the scrubber), to
// size the year samples from the grid's outer width.
const GRID_CHROME = 24 + 56;

/**
 * How the timeline is shown: the thumbnail size and the grouping (both
 * remembered) and, grouped by year, the per-year samples that replace the months. Created by the page,
 * during component setup (it owns a query).
 */
export class TimelineView {
	#size = new SizePreference(SIZE_KEY, () => legacy()?.size ?? null);
	grouping = $state<Grouping>(readGrouping());
	/** Newest or oldest first: remembered, like the grouping. */
	order = $state<TimelineOrder>(readOrder());
	/** Only the photos of a day or a range of days: for this visit only. */
	range = $state<DateRange | null>(null);

	get filter(): TimelineFilter {
		return { order: this.order, range: this.range };
	}

	/** Something narrows or reorders the photos: the filters' button says so. */
	get filtered() {
		return this.range !== null || this.order !== 'newest';
	}
	/** The grid's outer width, bound by the page: the year samples depend on it. */
	width = $state(0);

	#years = createQuery(() => ({
		...getTimelineYearsOptions({
			query: { sample: yearSampleSize(Math.max(0, this.width - GRID_CHROME), this.size) }
		}),
		enabled: this.grouping === 'year' && this.width > 0,
		// A new sample count (the slider moved) keeps the years on screen
		// until it arrives, instead of falling back to the months.
		placeholderData: (previous) => previous
	}));
	#months: () => GridSection<GridAsset>[];

	constructor(months: () => GridSection<GridAsset>[]) {
		this.#months = months;
	}

	/** Thumbnail size, 0–100 (zoom.ts). */
	get size() {
		return this.#size.value;
	}

	/** Showing the year samples (they arrive a moment after choosing the grouping). */
	yearView = $derived(this.grouping === 'year' && !!this.#years.data);

	#yearCounts = $derived(
		Object.fromEntries((this.#years.data ?? []).map((year) => [String(year.year), year.count]))
	);

	sections: GridSection<GridAsset>[] = $derived.by(() => {
		if (!this.yearView) return this.#months();
		const filter = this.filter;
		const years = (this.#years.data ?? [])
			.filter((year) => !filter.range || yearInRange(String(year.year), filter.range))
			.map((year) => {
				const items = arrangeItems(year.items.map(toGridAsset), filter);
				return { key: String(year.year), count: items.length, items };
			})
			.filter((year) => year.count > 0);
		return filter.order === 'oldest' ? years.reverse() : years;
	});

	sectionTitle = (key: string) => (key.length === 4 ? key : monthTitle(key));

	sectionSubtitle = (key: string) => {
		// A year's total counts every day of it, not just those of a range.
		if (this.range) return '';
		const count = this.#yearCounts[key];
		return count === undefined ? '' : m.timeline_year_items({ count });
	};

	setSize(size: number) {
		this.#size.set(size);
	}

	/** One step bigger (+1) or smaller (-1); `step` overrides the size of the step. */
	step(direction: 1 | -1, step?: number) {
		this.#size.step(direction, step);
	}

	setOrder(order: TimelineOrder) {
		this.order = order;
		try {
			localStorage.setItem(ORDER_KEY, order);
		} catch {
			// Storage may be unavailable (private mode); the choice lasts the visit.
		}
	}

	setRange(range: DateRange | null) {
		this.range = range;
	}

	setGrouping(grouping: Grouping) {
		this.grouping = grouping;
		try {
			localStorage.setItem(GROUPING_KEY, grouping);
		} catch {
			// Storage may be unavailable (private mode); the choice lasts the visit.
		}
	}
}

function legacy() {
	try {
		return migrateZoomLevel(localStorage.getItem(LEGACY_ZOOM_KEY));
	} catch {
		return null;
	}
}

function readGrouping(): Grouping {
	try {
		const own = localStorage.getItem(GROUPING_KEY);
		return own !== null ? parseGrouping(own) : (legacy()?.grouping ?? parseGrouping(null));
	} catch {
		return parseGrouping(null);
	}
}

function readOrder(): TimelineOrder {
	try {
		return localStorage.getItem(ORDER_KEY) === 'oldest' ? 'oldest' : 'newest';
	} catch {
		return 'newest';
	}
}
