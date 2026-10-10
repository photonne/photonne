<script lang="ts">
	import { tick } from 'svelte';
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		deleteApiFacesById,
		postApiFacesByIdAssign,
		postApiFacesByIdUnassign,
		type FaceDto
	} from '#lib/api/index.js';
	import { getApiAssetsByIdFacesOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { assignFaces } from '#lib/people/actions.js';
	import { invalidatePeople } from '#lib/people/cache.js';
	import { icons } from '#lib/people/icons.js';
	import { displayName, faceThumbnailUrl } from '#lib/people/people.js';
	import PersonPickerDialog, { type PersonPick } from '#lib/people/PersonPickerDialog.svelte';
	import { visibleFaces } from './faces.js';
	import PersonName from './PersonName.svelte';

	interface Props {
		assetId: string;
		/** The face picked here or on the photo. */
		selectedId: string | null;
		onselect: (faceId: string | null) => void;
		/** Whether the boxes are drawn over the photo. */
		showBoxes: boolean;
		ontoggleboxes: () => void;
	}

	let { assetId, selectedId, onselect, showBoxes, ontoggleboxes }: Props = $props();

	const queryClient = useQueryClient();
	const faces = createQuery(() => getApiAssetsByIdFacesOptions({ path: { id: assetId } }));
	const items = $derived(visibleFaces(faces.data ?? []));

	let picking = $state<FaceDto | null>(null);
	let busy = $state<string | null>(null);
	let list = $state<HTMLElement>();

	// A face picked on the photo scrolls into view here.
	$effect(() => {
		const id = selectedId;
		if (!id || !list) return;
		tick().then(() =>
			list?.querySelector(`[data-face="${id}"]`)?.scrollIntoView({ block: 'nearest' })
		);
	});

	async function run(faceId: string, call: () => Promise<{ error?: unknown }>) {
		if (busy) return false;
		busy = faceId;
		const { error } = await call();
		await Promise.all([
			invalidatePeople(queryClient),
			// The info panel's "same people" strip hangs off these faces.
			queryClient.invalidateQueries({ queryKey: [{ _id: 'getApiSearchPeopleByPersonIdAssets' }] })
		]);
		busy = null;
		if (error) toasts.error(m.action_failed());
		return !error;
	}

	async function assign(pick: PersonPick) {
		const face = picking;
		picking = null;
		if (!face) return;
		const name = pick.kind === 'person' ? displayName(pick, m.people_unnamed()) : pick.name;
		const done = await run(face.id, () =>
			postApiFacesByIdAssign({
				path: { id: face.id },
				body:
					pick.kind === 'person'
						? { personId: pick.id, newPersonName: null }
						: { personId: null, newPersonName: pick.name }
			})
		);
		if (done) toasts.show(m.viewer_face_assigned({ name }));
	}

	async function unassign(face: FaceDto) {
		const personId = face.personId;
		const done = await run(face.id, () => postApiFacesByIdUnassign({ path: { id: face.id } }));
		if (done && personId) {
			toasts.show(m.viewer_face_unassigned_toast(), {
				action: {
					label: m.action_undo(),
					run: () => assignFaces(queryClient, [face.id], personId)
				}
			});
		}
	}

	async function reject(face: FaceDto) {
		const personId = face.personId;
		const done = await run(face.id, () => deleteApiFacesById({ path: { id: face.id } }));
		if (!done) return;
		if (selectedId === face.id) onselect(null);
		// Assigning clears a rejection, so a face that had someone can come back.
		toasts.show(
			m.viewer_face_rejected_toast(),
			personId
				? {
						action: {
							label: m.action_undo(),
							run: () => assignFaces(queryClient, [face.id], personId)
						}
					}
				: {}
		);
	}
</script>

<div class="faces">
	<div class="head">
		<h3>{m.viewer_faces_title()}</h3>
		<button
			type="button"
			class="chip"
			aria-pressed={showBoxes}
			aria-keyshortcuts="Shift+F"
			title="{m.viewer_faces_boxes()} · {m.viewer_key_faces()}"
			onclick={ontoggleboxes}
		>
			<Icon path={icons.face} size={16} />
			{m.viewer_faces_boxes()}
		</button>
	</div>

	{#if faces.isPending}
		<p class="muted" role="status">{m.session_restoring()}</p>
	{:else if faces.isError}
		<p class="muted" role="alert">{m.error_loading()}</p>
	{:else if items.length === 0}
		<p class="muted">{m.viewer_faces_empty()}</p>
	{:else}
		<ul aria-label={m.viewer_faces_title()} bind:this={list}>
			{#each items as face (face.id)}
				<li data-face={face.id} class:selected={face.id === selectedId}>
					<button
						type="button"
						class="crop"
						aria-pressed={face.id === selectedId}
						aria-label={m.viewer_face_show()}
						onclick={() => onselect(face.id === selectedId ? null : face.id)}
					>
						<img src={faceThumbnailUrl(face.id)} alt="" loading="lazy" draggable="false" />
					</button>
					<div class="who">
						{#if face.personId}
							<a class="name" href={appHref(`/people/${face.personId}`)}
								><PersonName id={face.personId} /></a
							>
						{:else}
							<span class="name unknown">{m.viewer_face_unassigned()}</span>
							{#if face.suggestedPersonId}
								<span class="hint"
									>{m.viewer_face_suggested()} <PersonName id={face.suggestedPersonId} /></span
								>
							{/if}
						{/if}
					</div>
					<div class="actions">
						<button
							type="button"
							class="icon-btn sm"
							title={face.personId ? m.viewer_face_change() : m.viewer_face_assign()}
							aria-label={face.personId ? m.viewer_face_change() : m.viewer_face_assign()}
							disabled={busy !== null}
							onclick={() => (picking = face)}
						>
							<Icon path={icons.personMove} size={18} />
						</button>
						{#if face.personId}
							<button
								type="button"
								class="icon-btn sm"
								title={m.people_face_unassign()}
								aria-label={m.people_face_unassign()}
								disabled={busy !== null}
								onclick={() => unassign(face)}
							>
								<Icon path={icons.personRemove} size={18} />
							</button>
						{/if}
						<button
							type="button"
							class="icon-btn sm"
							title={m.people_face_reject()}
							aria-label={m.people_face_reject()}
							disabled={busy !== null}
							onclick={() => reject(face)}
						>
							<Icon path={icons.block} size={18} />
						</button>
					</div>
				</li>
			{/each}
		</ul>
	{/if}
</div>

<PersonPickerDialog
	open={picking !== null}
	title={picking?.personId ? m.viewer_face_change() : m.viewer_face_assign()}
	exclude={picking?.personId ? [picking.personId] : []}
	allowCreate
	onclose={() => (picking = null)}
	onpick={assign}
/>

<style>
	.faces {
		display: grid;
		gap: var(--space-3);
	}

	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
	}

	h3 {
		margin: 0;
		font-size: var(--font-size-sm);
		font-weight: 600;
		color: var(--color-text-muted);
	}

	.muted {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--space-1);
	}

	li {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1);
		border-radius: var(--radius-md);
	}

	li.selected {
		background: var(--color-surface);
		box-shadow: inset 0 0 0 1px var(--color-accent);
	}

	.crop {
		flex: none;
		width: 48px;
		height: 48px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		overflow: hidden;
		background: var(--color-surface);
		cursor: pointer;
	}

	.crop img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	.who {
		flex: 1;
		min-width: 0;
		display: grid;
	}

	.name {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		color: var(--color-text);
		text-decoration: none;
	}

	a.name:hover {
		color: var(--color-accent);
		text-decoration: underline;
	}

	.unknown,
	.hint {
		color: var(--color-text-muted);
	}

	.hint {
		font-size: var(--font-size-xs);
	}

	.actions {
		display: flex;
	}

	.actions .icon-btn {
		color: var(--color-text-muted);
	}

	.actions .icon-btn:hover:not(:disabled) {
		color: var(--color-text);
	}
</style>
