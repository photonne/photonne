<script lang="ts">
	import { tick } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { createInfiniteQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		postApiFacesByIdAcceptSuggestion,
		postApiFacesByIdDismissSuggestion,
		postApiPeopleByIdSuggestionsAcceptAll,
		postApiPeopleByIdSuggestionsDismissAll,
		type PersonDto
	} from '#lib/api/index.js';
	import {
		getApiPeopleByIdSuggestionsInfiniteOptions,
		getApiPeopleByIdQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Skeleton from '#lib/components/ui/Skeleton.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import AssetViewer from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import { invalidatePeople } from './cache.js';
	import { gridKeys } from './grid-keys.js';
	import { displayName, faceThumbnailUrl, nextOffset } from './people.js';
	import { whenVisible } from './visible.js';

	let { person }: { person: PersonDto } = $props();

	const queryClient = useQueryClient();
	const suggestions = createInfiniteQuery(() => ({
		...getApiPeopleByIdSuggestionsInfiniteOptions({
			path: { id: person.id },
			query: { limit: 120 }
		}),
		initialPageParam: 0,
		getNextPageParam: (last, pages) => nextOffset(pages, last.total)
	}));

	// Answered faces leave at once; the list itself refetches only when the
	// loaded ones run out, so reviewing doesn't reshuffle what's on screen.
	const answered = new SvelteSet<string>();
	const all = $derived(suggestions.data?.pages.flatMap((page) => page.items) ?? []);
	const items = $derived(all.filter((face) => !answered.has(face.id)));
	const total = $derived(suggestions.data?.pages[0]?.total ?? 0);
	const remaining = $derived(Math.max(0, total - answered.size));
	const name = $derived(displayName(person, m.people_unnamed()));

	const viewer = new ViewerRoute();
	const photoOrder = $derived([...new Set(items.map((face) => face.assetId))]);
	const neighbors = $derived(
		viewer.openId ? neighborsIn(photoOrder, viewer.openId) : { previous: null, next: null }
	);

	let grid = $state<HTMLElement>();
	let confirming = $state<'accept' | 'dismiss' | null>(null);
	let busy = $state(false);
	let accepted = 0;
	let dismissed = 0;
	let summaryTimer: ReturnType<typeof setTimeout> | undefined;

	// One toast per burst of answers instead of one per face.
	function summarize() {
		clearTimeout(summaryTimer);
		summaryTimer = setTimeout(() => {
			if (accepted) toasts.show(m.people_suggestions_accepted({ count: accepted }));
			if (dismissed) toasts.show(m.people_suggestions_dismissed({ count: dismissed }));
			accepted = dismissed = 0;
		}, 1200);
	}

	async function answer(faceId: string, accept: boolean) {
		const cells = grid ? [...grid.querySelectorAll<HTMLElement>('[data-cell]')] : [];
		const index = cells.findIndex((cell) => cell.dataset.face === faceId);
		const hadFocus = index >= 0 && cells[index].contains(document.activeElement);
		answered.add(faceId);
		// Keep the keyboard where it was: on the face that slid into this place.
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
		if (accept) accepted++;
		else dismissed++;
		summarize();
		queryClient.invalidateQueries({
			queryKey: getApiPeopleByIdQueryKey({ path: { id: person.id } })
		});
		queryClient.invalidateQueries({ queryKey: [{ _id: 'getApiPeople' }] });
		if (items.length === 0) refresh();
	}

	async function refresh() {
		await invalidatePeople(queryClient);
		answered.clear();
	}

	async function bulk() {
		const mode = confirming;
		if (!mode || busy) return;
		busy = true;
		const call =
			mode === 'accept'
				? postApiPeopleByIdSuggestionsAcceptAll
				: postApiPeopleByIdSuggestionsDismissAll;
		const { data, error } = await call({ path: { id: person.id } });
		busy = false;
		confirming = null;
		if (error || !data) {
			toasts.error(m.action_failed());
			return;
		}
		toasts.show(
			mode === 'accept'
				? m.people_suggestions_accepted({ count: data.affected })
				: m.people_suggestions_dismissed({ count: data.affected })
		);
		await refresh();
	}

	// A, D and Enter on a focused card (not on its buttons, which keep their own keys).
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
	<div role="alert"><EmptyState icon="info" title={m.error_loading()} /></div>
{:else if items.length === 0}
	<EmptyState icon="check" title={m.people_suggestions_empty()} />
{:else}
	<div class="intro">
		<div>
			<p>{m.people_suggestions_intro({ name })}</p>
			<p class="hint">{m.people_suggestions_hint()}</p>
		</div>
		<div class="bulk">
			<button type="button" class="btn" onclick={() => (confirming = 'dismiss')}>
				{m.people_suggestions_dismiss_all()}
			</button>
			<button type="button" class="btn primary" onclick={() => (confirming = 'accept')}>
				{m.people_suggestions_accept_all()}
			</button>
		</div>
	</div>
	<ul
		class="grid"
		aria-label={m.people_tab_suggestions()}
		bind:this={grid}
		{@attach gridKeys}
		{@attach cardKeys}
	>
		{#each items as face, index (face.id)}
			<li>
				<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
				<div
					class="card"
					role="group"
					tabindex="0"
					data-cell
					data-face={face.id}
					data-asset={face.assetId}
					aria-label={m.people_suggestion_item({ index: index + 1 })}
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
	{#if suggestions.hasNextPage}
		{#key suggestions.data?.pages.length}
			<div
				class="sentinel"
				{@attach whenVisible(() => {
					if (!suggestions.isFetchingNextPage) suggestions.fetchNextPage();
				})}
			></div>
		{/key}
	{/if}
{/if}

<Dialog
	open={confirming !== null}
	title={confirming === 'accept'
		? m.people_suggestions_accept_all()
		: m.people_suggestions_dismiss_all()}
	onclose={() => (confirming = null)}
>
	<p class="confirm">
		{confirming === 'accept'
			? m.people_suggestions_accept_all_body({ count: remaining, name })
			: m.people_suggestions_dismiss_all_body({ count: remaining, name })}
	</p>
	{#snippet actions()}
		<button type="button" onclick={() => (confirming = null)}>{m.people_cancel()}</button>
		<button
			type="button"
			class={confirming === 'accept' ? 'primary' : 'danger'}
			disabled={busy}
			onclick={bulk}
		>
			{confirming === 'accept'
				? m.people_suggestions_accept_all()
				: m.people_suggestions_dismiss_all()}
		</button>
	{/snippet}
</Dialog>

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
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3) var(--space-4);
		padding: var(--space-3) var(--page-gutter);
	}

	.intro p {
		margin: 0;
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.bulk {
		margin-left: auto;
		display: flex;
		gap: var(--space-2);
	}

	.grid {
		list-style: none;
		margin: 0;
		padding: 0 var(--page-gutter) var(--space-8);
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(136px, 1fr));
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

	.confirm {
		margin: 0;
	}

	.sentinel {
		height: 1px;
	}
</style>
