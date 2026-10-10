<script lang="ts" module>
	export type PersonTab = 'photos' | 'faces' | 'suggestions';
</script>

<script lang="ts">
	import type { PersonDto } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import Tabs from '#lib/components/ui/Tabs.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from './icons.js';
	import PersonAvatar from './PersonAvatar.svelte';

	/**
	 * Under the person's name, on each of their pages: the face as a hero, what
	 * there is of them, and the tabs (Fotos · Caras · Sugerencias).
	 */
	let { person, current }: { person: PersonDto; current: PersonTab } = $props();

	const pending = $derived(person.pendingSuggestionsCount);

	const tabs = $derived([
		{
			href: appHref(`/people/${person.id}`),
			label: m.people_tab_photos(),
			current: current === 'photos'
		},
		{
			href: appHref(`/people/${person.id}/faces`),
			label: m.people_tab_faces(),
			current: current === 'faces'
		},
		{
			href: appHref(`/people/${person.id}/suggestions`),
			// The pending count reads as part of the tab's name ("Sugerencias 3").
			label: pending ? `${m.people_tab_suggestions()} ${pending}` : m.people_tab_suggestions(),
			current: current === 'suggestions'
		}
	]);
</script>

<div class="hero">
	<PersonAvatar faceId={person.coverFaceId} name={person.name} size="96px" />
	<div class="facts">
		<span class="faces">{m.people_faces({ count: person.faceCount })}</span>
		{#if pending}
			<span class="chip fact pending">{m.people_pending_badge({ count: pending })}</span>
		{/if}
		{#if person.isHidden}
			<span class="chip fact">
				<Icon path={icons.visibilityOff} size={14} />
				{m.people_hidden()}
			</span>
		{/if}
	</div>
</div>
<Tabs label={m.people_tabs()} {tabs} />

<style>
	.hero {
		display: flex;
		align-items: center;
		gap: var(--space-4);
		padding: var(--space-1) var(--page-gutter) var(--space-4);
	}

	.facts {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
		min-width: 0;
	}

	.faces {
		margin-right: var(--space-1);
		color: var(--color-text-muted);
		font-size: var(--font-size-md);
	}

	/* Facts, not filters: the chip's look without its pointer. */
	.fact {
		cursor: default;
	}

	.fact:hover {
		background: transparent;
	}

	.pending,
	.pending:hover {
		border-color: var(--color-accent);
		background: var(--color-accent-soft);
	}
</style>
