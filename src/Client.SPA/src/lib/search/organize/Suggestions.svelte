<script lang="ts" module>
	const KEY = 'photonne.organize.suggestions.collapsed';

	// Folded to make room for the inbox, remembered across visits.
	function readCollapsed(): boolean {
		try {
			return localStorage.getItem(KEY) === '1';
		} catch {
			return false;
		}
	}
</script>

<script lang="ts">
	import type { OrganizeSuggestionResponse } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { formatCount } from '#lib/format.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { suggestionRange, suggestionTitle } from './suggestion.js';

	interface Props {
		suggestions: readonly OrganizeSuggestionResponse[];
		busy?: boolean;
		onreview: (suggestion: OrganizeSuggestionResponse) => void;
		ondismiss: (suggestion: OrganizeSuggestionResponse) => void;
	}

	let { suggestions, busy = false, onreview, ondismiss }: Props = $props();

	const kinds: Record<string, () => string> = {
		trip: m.organize_kind_trip,
		person: m.organize_kind_person,
		scene: m.organize_kind_scene,
		month: m.organize_kind_month
	};

	const listId = $props.id();
	let open = $state(!readCollapsed());

	function toggle() {
		open = !open;
		try {
			localStorage.setItem(KEY, open ? '0' : '1');
		} catch {
			// Remembered for this page only.
		}
	}
</script>

<section class="suggestions" aria-labelledby="organize-suggestions">
	<h2 id="organize-suggestions">
		<button type="button" aria-expanded={open} aria-controls={listId} onclick={toggle}>
			<span class="chevron" class:open aria-hidden="true"
				><Icon name="chevronRight" size={18} /></span
			>
			{m.organize_suggestions()}
			<span class="count">{formatCount(suggestions.length)}</span>
		</button>
	</h2>
	<ul id={listId} hidden={!open}>
		{#each suggestions as suggestion (suggestion.key)}
			{@const title = suggestionTitle(suggestion)}
			<li>
				<article aria-label={title}>
					{#if suggestion.coverAssetId}
						<img src={thumbnailUrl(suggestion.coverAssetId, 'Small')} alt="" loading="lazy" />
					{:else}
						<span class="cover" aria-hidden="true"><Icon name="photos" /></span>
					{/if}
					<div class="text">
						<h3>{title}</h3>
						<p>
							{kinds[suggestion.kind]?.() ?? suggestion.kind} · {m.organize_photo_count({
								count: suggestion.count
							})}
						</p>
						{#if suggestion.kind !== 'month' && suggestionRange(suggestion)}
							<p>{suggestionRange(suggestion)}</p>
						{/if}
					</div>
					<div class="buttons">
						<button
							type="button"
							class="btn sm primary"
							disabled={busy}
							aria-label={m.organize_review_suggestion({ title })}
							onclick={() => onreview(suggestion)}
						>
							{m.organize_review_and_move()}
						</button>
						<button
							type="button"
							class="btn sm ghost"
							disabled={busy}
							aria-label={m.organize_dismiss_suggestion({ title })}
							onclick={() => ondismiss(suggestion)}
						>
							{m.organize_dismiss()}
						</button>
					</div>
				</article>
			</li>
		{/each}
	</ul>
</section>

<style>
	.suggestions {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-3) var(--page-gutter) var(--space-2);
	}

	h2 {
		margin: 0;
	}

	h2 button {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		min-height: var(--control-h-sm);
		margin-left: calc(-1 * var(--space-1));
		padding: 0 var(--space-2) 0 var(--space-1);
		border: 0;
		border-radius: var(--radius-control);
		background: transparent;
		color: var(--color-text);
		font: inherit;
		font-size: var(--font-size-md);
		font-weight: 650;
		line-height: 1.3;
		cursor: pointer;
	}

	h2 button:hover {
		background: var(--color-surface-raised);
	}

	.chevron {
		display: grid;
		color: var(--color-text-muted);
		transition: transform var(--duration-fast);
	}

	.chevron.open {
		transform: rotate(90deg);
	}

	@media (prefers-reduced-motion: reduce) {
		.chevron {
			transition: none;
		}
	}

	.count {
		margin-left: var(--space-1);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		font-weight: 400;
	}

	ul[hidden] {
		display: none;
	}

	ul {
		display: flex;
		gap: var(--space-3);
		margin: 0;
		padding: 0 0 var(--space-2);
		list-style: none;
		overflow-x: auto;
	}

	li {
		display: flex;
	}

	/* The buttons sit at the card's foot, so they line up across cards
	   whatever the text above them takes. */
	article {
		display: grid;
		grid-template-columns: 64px 1fr;
		grid-template-rows: auto 1fr;
		gap: var(--space-3);
		width: 300px;
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
	}

	img,
	.cover {
		width: 64px;
		height: 64px;
		border-radius: var(--radius-sm);
		object-fit: cover;
		background: var(--color-brand-tile);
	}

	.cover {
		display: grid;
		place-items: center;
		color: var(--color-accent);
	}

	.text {
		min-width: 0;
	}

	h3 {
		margin: 0;
		overflow: hidden;
		font-size: var(--font-size-md);
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	p {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.buttons {
		grid-column: 1 / -1;
		align-self: end;
		display: flex;
		gap: var(--space-2);
	}
</style>
