<script lang="ts" module>
	export type PersonTab = 'photos' | 'faces' | 'suggestions';
</script>

<script lang="ts">
	import type { PersonDto } from '#lib/api/index.js';
	import Icon from '#lib/components/Icon.svelte';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from './icons.js';
	import PersonAvatar from './PersonAvatar.svelte';

	let { person, current }: { person: PersonDto; current: PersonTab } = $props();

	const tabs = $derived([
		{ key: 'photos' as const, path: `/people/${person.id}`, label: m.people_tab_photos() },
		{ key: 'faces' as const, path: `/people/${person.id}/faces`, label: m.people_tab_faces() },
		{
			key: 'suggestions' as const,
			path: `/people/${person.id}/suggestions`,
			label: m.people_tab_suggestions(),
			badge: person.pendingSuggestionsCount
		}
	]);
</script>

<div class="bar">
	<PersonAvatar faceId={person.coverFaceId} name={person.name} size="40px" />
	<span class="summary">
		{m.people_faces({ count: person.faceCount })}
		{#if person.isHidden}
			<span class="hidden"><Icon path={icons.visibilityOff} size={14} /> {m.people_hidden()}</span>
		{/if}
	</span>
	<nav aria-label={m.people_tabs()}>
		{#each tabs as tab (tab.key)}
			<a
				href={appHref(tab.path)}
				aria-current={tab.key === current ? 'page' : undefined}
				data-sveltekit-replacestate
				data-sveltekit-noscroll
			>
				{tab.label}
				{#if tab.badge}
					<span class="badge" title={m.people_pending_badge({ count: tab.badge })}>
						{tab.badge}
						<span class="visually-hidden">{m.people_pending_badge({ count: tab.badge })}</span>
					</span>
				{/if}
			</a>
		{/each}
	</nav>
</div>

<style>
	.bar {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4) 0;
		border-bottom: 1px solid var(--color-border);
	}

	.summary {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
		white-space: nowrap;
	}

	.hidden {
		display: inline-flex;
		align-items: center;
		gap: 2px;
	}

	nav {
		display: flex;
		gap: var(--space-1);
		margin-left: var(--space-4);
		align-self: stretch;
	}

	a {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-2) var(--space-3);
		border-bottom: 2px solid transparent;
		color: var(--color-text-muted);
		text-decoration: none;
		font-weight: 600;
	}

	a:hover {
		color: var(--color-text);
	}

	a[aria-current='page'] {
		border-bottom-color: var(--color-accent);
		color: var(--color-text);
	}

	.badge {
		min-width: 20px;
		padding: 0 6px;
		border-radius: 10px;
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-size: var(--font-size-xs);
		line-height: 20px;
		text-align: center;
	}
</style>
