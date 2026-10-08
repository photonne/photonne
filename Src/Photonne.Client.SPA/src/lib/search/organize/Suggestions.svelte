<script lang="ts">
	import type { OrganizeSuggestionResponse } from '#lib/api/index.js';
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
</script>

<section class="suggestions" aria-labelledby="organize-suggestions">
	<h2 id="organize-suggestions">{m.organize_suggestions()}</h2>
	<ul>
		{#each suggestions as suggestion (suggestion.key)}
			{@const title = suggestionTitle(suggestion)}
			<li>
				<article aria-label={title}>
					{#if suggestion.coverAssetId}
						<img src={thumbnailUrl(suggestion.coverAssetId, 'Small')} alt="" loading="lazy" />
					{:else}
						<span class="cover" aria-hidden="true"></span>
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
							class="primary"
							disabled={busy}
							aria-label={m.organize_review_suggestion({ title })}
							onclick={() => onreview(suggestion)}
						>
							{m.organize_review_and_move()}
						</button>
						<button
							type="button"
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
		padding: 0 var(--space-4) var(--space-2);
	}

	h2 {
		margin: 0 0 var(--space-2);
		font-size: var(--font-size-md);
	}

	ul {
		display: flex;
		gap: var(--space-3);
		margin: 0;
		padding: 0 0 var(--space-2);
		list-style: none;
		overflow-x: auto;
	}

	article {
		display: grid;
		grid-template-columns: 64px 1fr;
		grid-template-rows: auto auto;
		gap: var(--space-2) var(--space-3);
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
		background: var(--color-placeholder);
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
		display: flex;
		gap: var(--space-2);
	}

	button {
		padding: var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	button.primary {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
	}

	button:disabled {
		opacity: 0.5;
		cursor: progress;
	}
</style>
