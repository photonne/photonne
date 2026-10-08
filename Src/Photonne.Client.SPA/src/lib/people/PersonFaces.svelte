<script lang="ts">
	import { tick } from 'svelte';
	import { createInfiniteQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		deleteApiFacesById,
		postApiFacesByIdAssign,
		postApiFacesByIdUnassign,
		postApiPeopleByIdCoverByFaceId,
		type PersonDto
	} from '#lib/api/index.js';
	import { getApiPeopleByIdFacesInfiniteOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import SelectionBar from '#lib/timeline/SelectionBar.svelte';
	import { Selection } from '#lib/timeline/selection.svelte.js';
	import AssetViewer from '#lib/viewer/AssetViewer.svelte';
	import { neighborsIn } from '#lib/viewer/neighbors.js';
	import { ViewerRoute } from '#lib/viewer/viewer-route.svelte.js';
	import { assignFaces, eachUntilError } from './actions.js';
	import { invalidatePeople } from './cache.js';
	import { gridKeys } from './grid-keys.js';
	import { icons } from './icons.js';
	import { displayName, faceThumbnailUrl, nextOffset } from './people.js';
	import PersonPickerDialog, { type PersonPick } from './PersonPickerDialog.svelte';
	import { whenVisible } from './visible.js';

	let { person }: { person: PersonDto } = $props();

	const queryClient = useQueryClient();
	const faces = createInfiniteQuery(() => ({
		...getApiPeopleByIdFacesInfiniteOptions({ path: { id: person.id }, query: { limit: 120 } }),
		initialPageParam: 0,
		getNextPageParam: (last, pages) => nextOffset(pages, last.total)
	}));

	const items = $derived(faces.data?.pages.flatMap((page) => page.items) ?? []);
	const order = $derived(items.map((face) => face.id));
	// The viewer walks the photos behind the faces, each once.
	const photoOrder = $derived([...new Set(items.map((face) => face.assetId))]);

	const selection = new Selection();
	const viewer = new ViewerRoute();
	const neighbors = $derived(
		viewer.openId ? neighborsIn(photoOrder, viewer.openId) : { previous: null, next: null }
	);

	let moving = $state(false);
	let busy = $state(false);
	let grid = $state<HTMLElement>();

	$effect(() => {
		const present = new Set(order);
		const gone = [...selection.ids].filter((id) => !present.has(id));
		if (gone.length) selection.set(gone, false);
	});

	function onFaceClick(event: MouseEvent, faceId: string) {
		if (event.shiftKey) selection.selectRange(faceId, order);
		else selection.toggle(faceId);
	}

	function onFaceKey(event: KeyboardEvent, assetId: string) {
		if (event.key === 'Enter') {
			event.preventDefault();
			viewer.open(assetId);
		}
	}

	/** Runs a change on the selected faces; Undo puts them back on this person. */
	async function change(
		call: (faceId: string) => Promise<{ error?: unknown }>,
		message: (count: number) => string
	) {
		if (busy) return;
		busy = true;
		const ids = [...selection.ids];
		const done = await eachUntilError(ids, call);
		const changed = ids.slice(0, done);
		selection.clear();
		await invalidatePeople(queryClient);
		busy = false;
		if (done < ids.length) toasts.error(m.action_failed());
		if (done > 0) {
			const personId = person.id;
			toasts.show(message(done), {
				action: { label: m.action_undo(), run: () => assignFaces(queryClient, changed, personId) }
			});
		}
	}

	const unassign = () =>
		change(
			(id) => postApiFacesByIdUnassign({ path: { id } }),
			(count) => m.people_faces_unassigned({ count })
		);

	const reject = () =>
		change(
			(id) => deleteApiFacesById({ path: { id } }),
			(count) => m.people_faces_rejected({ count })
		);

	async function moveTo(pick: PersonPick) {
		moving = false;
		let target = pick.kind === 'person' ? pick.id : null;
		const targetName = pick.kind === 'person' ? displayName(pick, m.people_unnamed()) : pick.name;
		await change(
			async (id) => {
				// The first face creates the new person; the rest join it.
				const { data, error } = await postApiFacesByIdAssign({
					path: { id },
					body: target
						? { personId: target, newPersonName: null }
						: { personId: null, newPersonName: pick.name }
				});
				if (data?.personId) target = data.personId;
				return { error };
			},
			(count) => m.people_faces_moved({ count, name: targetName })
		);
	}

	async function setCover() {
		const [faceId] = selection.ids;
		if (!faceId || busy) return;
		busy = true;
		const { error } = await postApiPeopleByIdCoverByFaceId({ path: { id: person.id, faceId } });
		busy = false;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		selection.clear();
		await invalidatePeople(queryClient);
		toasts.show(m.people_cover_set());
	}

	async function closeViewer() {
		const last = await viewer.close();
		await tick();
		if (last) grid?.querySelector<HTMLElement>(`[data-asset="${last}"]`)?.focus();
	}

	function onkeydown(event: KeyboardEvent) {
		if (event.key !== 'Escape' || !selection.active || moving || viewer.openId) return;
		if ((event.target as HTMLElement).closest('input, dialog')) return;
		selection.clear();
	}
</script>

<svelte:window {onkeydown} />

<SelectionBar {selection}>
	{#snippet actions()}
		<button
			type="button"
			class="action"
			disabled={selection.size !== 1 || busy}
			title={m.people_face_set_cover()}
			aria-label={m.people_face_set_cover()}
			onclick={setCover}
		>
			<Icon path={icons.star} />
		</button>
		<button
			type="button"
			class="action"
			disabled={busy}
			title={m.people_face_move()}
			aria-label={m.people_face_move()}
			onclick={() => (moving = true)}
		>
			<Icon path={icons.personMove} />
		</button>
		<button
			type="button"
			class="action"
			disabled={busy}
			title={m.people_face_unassign()}
			aria-label={m.people_face_unassign()}
			onclick={unassign}
		>
			<Icon path={icons.personRemove} />
		</button>
		<button
			type="button"
			class="action"
			disabled={busy}
			title={m.people_face_reject()}
			aria-label={m.people_face_reject()}
			onclick={reject}
		>
			<Icon path={icons.block} />
		</button>
	{/snippet}
</SelectionBar>

{#if faces.isPending}
	<p class="status" role="status">{m.session_restoring()}</p>
{:else if faces.isError}
	<p class="status" role="alert">{m.error_loading()}</p>
{:else if items.length === 0}
	<p class="status">{m.people_faces_empty()}</p>
{:else}
	<p class="hint">{m.people_faces_hint()}</p>
	<ul class="grid" aria-label={m.people_tab_faces()} bind:this={grid} {@attach gridKeys}>
		{#each items as face, index (face.id)}
			{@const isCover = face.id === person.coverFaceId}
			{@const isSelected = selection.has(face.id)}
			<li class:selected={isSelected}>
				<button
					type="button"
					class="face"
					data-cell
					data-asset={face.assetId}
					aria-pressed={isSelected}
					aria-label={isCover
						? m.people_face_item_cover({ index: index + 1 })
						: m.people_face_item({ index: index + 1 })}
					onclick={(event) => onFaceClick(event, face.id)}
					ondblclick={() => viewer.open(face.assetId)}
					onkeydown={(event) => onFaceKey(event, face.assetId)}
				>
					<img src={faceThumbnailUrl(face.id)} alt="" loading="lazy" draggable="false" />
					<span class="check" aria-hidden="true"><Icon name="check" size={14} /></span>
					{#if isCover}
						<span class="cover" aria-hidden="true">
							<Icon path={icons.star} size={12} />
							{m.people_face_cover()}
						</span>
					{/if}
				</button>
				<button
					type="button"
					class="open"
					tabindex="-1"
					title={m.people_face_open()}
					aria-label={m.people_face_open()}
					onclick={() => viewer.open(face.assetId)}
				>
					<Icon path={icons.openPhoto} size={16} />
				</button>
			</li>
		{/each}
	</ul>
	{#if faces.hasNextPage}
		{#key faces.data?.pages.length}
			<div
				class="sentinel"
				{@attach whenVisible(() => {
					if (!faces.isFetchingNextPage) faces.fetchNextPage();
				})}
			></div>
		{/key}
	{/if}
{/if}

<PersonPickerDialog
	open={moving}
	title={m.people_face_move()}
	exclude={[person.id]}
	allowCreate
	onclose={() => (moving = false)}
	onpick={moveTo}
/>

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
	.status,
	.hint {
		padding: var(--space-3) var(--space-4);
		margin: 0;
		color: var(--color-text-muted);
	}

	.hint {
		font-size: var(--font-size-sm);
	}

	.grid {
		list-style: none;
		margin: 0;
		padding: 0 var(--space-4) var(--space-8);
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
		gap: var(--space-2);
	}

	li {
		position: relative;
	}

	.face {
		position: relative;
		display: block;
		width: 100%;
		aspect-ratio: 1;
		padding: 0;
		border: 0;
		border-radius: var(--radius-md);
		overflow: hidden;
		background: var(--color-placeholder);
		cursor: pointer;
	}

	.face img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: transform var(--duration-fast);
	}

	.selected .face {
		outline: 3px solid var(--color-accent);
		outline-offset: -3px;
	}

	.selected .face img {
		transform: scale(0.9);
	}

	.check {
		position: absolute;
		top: var(--space-1);
		left: var(--space-1);
		display: grid;
		place-items: center;
		width: 22px;
		height: 22px;
		border: 2px solid #fff;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.35);
		color: transparent;
		opacity: 0;
	}

	li:hover .check,
	.face:focus-visible .check,
	.grid:has(.selected) .check {
		opacity: 1;
	}

	.selected .check {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
	}

	.cover {
		position: absolute;
		left: var(--space-1);
		bottom: var(--space-1);
		display: flex;
		align-items: center;
		gap: 2px;
		padding: 0 var(--space-1);
		border-radius: var(--radius-sm);
		background: rgb(0 0 0 / 0.6);
		color: #fff;
		font-size: var(--font-size-xs);
	}

	.open {
		position: absolute;
		top: var(--space-1);
		right: var(--space-1);
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.55);
		color: #fff;
		opacity: 0;
		cursor: pointer;
	}

	li:hover .open {
		opacity: 1;
	}

	.sentinel {
		height: 1px;
	}

	.action {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		cursor: pointer;
	}

	.action:hover:not(:disabled) {
		background: var(--color-surface);
	}

	.action:disabled {
		opacity: 0.35;
		cursor: default;
	}
</style>
