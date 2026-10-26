<script lang="ts">
	import { tick } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { createInfiniteQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		postApiFacesByIdAcceptSuggestion,
		postApiFacesByIdDismissSuggestion
	} from '#lib/api/index.js';
	import { listFaceSuggestionsInfiniteOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import AssetViewer from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import { invalidatePeople } from './cache.js';
	import { gridKeys } from './grid-keys.js';
	import { displayName, faceThumbnailUrl, nextOffset, uniqueById } from './people.js';
	import LoadMore from './LoadMore.svelte';
	import PersonAvatar from './PersonAvatar.svelte';

	/**
	 * "¿Quién es?": every face the recognition thinks could be someone, of
	 * anyone, strongest first. The same yes/no as a person's suggestions tab
	 * (A and D on a focused card), without going person by person.
	 */

	const queryClient = useQueryClient();
	const suggestions = createInfiniteQuery(() => ({
		...listFaceSuggestionsInfiniteOptions({ query: { limit: 120 } }),
		initialPageParam: 0,
		getNextPageParam: (last, pages) => nextOffset(pages, last.total, (face) => settled.has(face.id))
	}));

	const answered = new SvelteSet<string>();
	const settled = new SvelteSet<string>();
	const all = $derived(uniqueById(suggestions.data?.pages.flatMap((page) => page.items) ?? []));
	const items = $derived(all.filter((face) => !answered.has(face.id)));
	const total = $derived(suggestions.data?.pages[0]?.total ?? 0);
	const remaining = $derived(Math.max(0, total - answered.size));

	const viewer = new ViewerRoute();
	const photoOrder = $derived([...new Set(items.map((face) => face.assetId))]);
	const neighbors = $derived(
		viewer.openId ? neighborsIn(photoOrder, viewer.openId) : { previous: null, next: null }
	);

	let grid = $state<HTMLElement>();
	let accepted = 0;
	let dismissed = 0;
	let summaryTimer: ReturnType<typeof setTimeout> | undefined;

	function summarize() {
		clearTimeout(summaryTimer);
		summaryTimer = setTimeout(() => {
			if (accepted) toasts.show(m.people_suggestions_accepted({ count: accepted }));
			if (dismissed) toasts.show(m.people_suggestions_dismissed({ count: dismissed }));
			accepted = dismissed = 0;
		}, 1200);
	}

	const nameOf = (face: { personName?: string | null }) =>
		displayName({ name: face.personName ?? null }, m.people_unnamed());

	async function answer(faceId: string, accept: boolean) {
		const cells = grid ? [...grid.querySelectorAll<HTMLElement>('[data-cell]')] : [];
		const index = cells.findIndex((cell) => cell.dataset.face === faceId);
		const hadFocus = index >= 0 && cells[index].contains(document.activeElement);
		answered.add(faceId);
		if (hadFocus) {
			await tick();
			const next = grid?.querySelectorAll<HTMLElement>('[data-cell]');
			next?.[Math.min(index, next.length - 1)]?.focus();
		}
		const { error } = accept
			? await postApiFacesByIdAcceptSuggestion({ path: { id: faceId } })
			: await postApiFacesByIdDismissSuggestion({ path: { id: faceId } });
		if (error) {
			answered.delete(faceId);
			toasts.error(m.action_failed());
			return;
		}
		settled.add(faceId);
		if (accept) accepted++;
		else dismissed++;
		summarize();
		queryClient.invalidateQueries({ queryKey: [{ _id: 'getApiPeople' }] });
		if (items.length === 0) {
			await invalidatePeople(queryClient);
			answered.clear();
			settled.clear();
		}
	}

	function cardKeys(node: HTMLElement) {
		function onkeydown(event: KeyboardEvent) {
			const card = event.target as HTMLElement;
			if (event.altKey || event.ctrlKey || event.metaKey || !card.matches('[data-cell]')) return;
			const faceId = card.dataset.face!;
			const key = event.key.toLowerCase();
			if (key === 'a') answer(faceId, true);
			else if (key === 'd') answer(faceId, false);
			else if (key === 'enter') viewer.open(card.dataset.asset!);
			else return;
			event.preventDefault();
		}
		node.addEventListener('keydown', onkeydown);
		return () => node.removeEventListener('keydown', onkeydown);
	}

	async function closeViewer() {
		const last = await viewer.close();
		await tick();
		if (last) grid?.querySelector<HTMLElement>(`[data-asset="${last}"]`)?.focus();
	}
</script>

{#if suggestions.isPending}
	<Skeleton variant="cards" count={12} />
{:else if suggestions.isError}
	<div role="alert">
		<EmptyState icon="info" title={m.error_loading_title()} hint={m.error_loading_hint()} />
	</div>
{:else if items.length === 0}
	<EmptyState icon="check" title={m.people_queue_empty()} hint={m.people_queue_empty_hint()} />
{:else}
	<div class="intro">
		<p>{m.people_queue_intro({ count: remaining })}</p>
		<p class="hint">{m.people_suggestions_hint()}</p>
	</div>
	<ul
		class="grid"
		aria-label={m.people_queue_title()}
		bind:this={grid}
		{@attach gridKeys}
		{@attach cardKeys}
	>
		{#each items as face, index (face.id)}
			{@const name = nameOf(face)}
			<li>
				<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
				<div
					class="card"
					role="group"
					tabindex="0"
					data-cell
					data-face={face.id}
					data-asset={face.assetId}
					aria-label={m.people_queue_item({ index: index + 1, name })}
				>
					<button
						type="button"
						class="photo"
						tabindex="-1"
						title={m.people_face_open()}
						aria-label={m.people_face_open()}
						onclick={() => viewer.open(face.assetId)}
					>
						<img src={faceThumbnailUrl(face.id)} alt="" loading="lazy" draggable="false" />
					</button>
					<a class="who" href={appHref(`/people/${face.personId}`)} tabindex="-1">
						<PersonAvatar faceId={face.personCoverFaceId} name={face.personName} size="24px" />
						<span>{m.people_queue_question({ name })}</span>
					</a>
					<div class="answers">
						<button
							type="button"
							class="btn sm no"
							title={m.people_suggestion_dismiss({ name })}
							aria-label={m.people_suggestion_dismiss({ name })}
							onclick={() => answer(face.id, false)}
						>
							<Icon name="close" size={18} />
						</button>
						<button
							type="button"
							class="btn sm yes"
							title={m.people_suggestion_accept({ name })}
							aria-label={m.people_suggestion_accept({ name })}
							onclick={() => answer(face.id, true)}
						>
							<Icon name="check" size={18} />
						</button>
					</div>
				</div>
			</li>
		{/each}
	</ul>
	<LoadMore query={suggestions} />
{/if}

{#if viewer.openId}
	<AssetViewer
		assetId={viewer.openId}
		previous={neighbors.previous}
		next={neighbors.next}
		onnavigate={(id) => viewer.navigate(id)}
		onclose={closeViewer}
		onchanged={() => {}}
	/>
{/if}

<style>
	.intro {
		padding: var(--space-3) var(--page-gutter);
	}

	.intro p {
		margin: 0;
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.grid {
		list-style: none;
		margin: 0;
		padding: 0 var(--page-gutter) var(--space-8);
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: var(--space-3);
	}

	.card {
		display: grid;
		gap: var(--space-2);
		padding: var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface-raised);
	}

	.card:focus-visible {
		outline: 3px solid var(--color-focus);
		outline-offset: 1px;
	}

	.photo {
		display: block;
		aspect-ratio: 1;
		padding: 0;
		border: 0;
		border-radius: 50%;
		overflow: hidden;
		background: var(--color-placeholder);
		cursor: zoom-in;
	}

	.photo img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.who {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
		color: inherit;
		font-size: var(--font-size-sm);
		text-decoration: none;
	}

	.who span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.answers {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-2);
	}

	.answers .btn {
		padding: 0;
	}

	.answers .no:hover {
		border-color: var(--color-danger);
		color: var(--color-danger);
	}

	.answers .yes:hover {
		border-color: var(--color-accent);
		background: var(--color-accent-soft);
	}
</style>
