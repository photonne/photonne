<script lang="ts">
	import Dialog from '#lib/components/Dialog.svelte';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import { groupState, keptIds, toggleGroup, toggleId, type ReviewGroup } from './review.js';

	interface Props {
		open: boolean;
		title: string;
		groups: readonly ReviewGroup[];
		/** The confirm button ("Choose destination", "Move"). */
		confirmLabel: string;
		busy?: boolean;
		onclose: () => void;
		/**
		 * `kept`: what still moves. `edited`: whether anything was taken out
		 * (a rule can then move on the server without sending the ids).
		 */
		onconfirm: (kept: string[], edited: boolean) => void;
	}

	let { open, title, groups, confirmLabel, busy = false, onclose, onconfirm }: Props = $props();

	// Moving is physical and has no undo, so this last look lets photos out.
	let excluded = $state<ReadonlySet<string>>(new Set());
	$effect(() => {
		if (open) excluded = new Set();
	});

	const total = $derived(groups.reduce((sum, group) => sum + group.ids.length, 0));
	const kept = $derived(total - excluded.size);

	const toggle = (id: string) => (excluded = toggleId(excluded, id));
</script>

<Dialog {open} {title} {onclose} width="880px">
	<p class="lead" aria-live="polite">
		{m.organize_review_count({ kept, total })}
	</p>
	<div class="groups">
		{#each groups as group (group.label)}
			{@const level = groupState(group, excluded)}
			<section aria-label={group.label}>
				<label class="year">
					<input
						type="checkbox"
						aria-label={m.organize_review_group({ group: group.label })}
						checked={level === 'all'}
						indeterminate={level === 'some'}
						onchange={() => (excluded = toggleGroup(group, excluded))}
					/>
					<span>{group.label}</span>
					<span class="count">{m.organize_photo_count({ count: group.ids.length })}</span>
				</label>
				<ul>
					{#each group.ids as id, index (id)}
						<li class:out={excluded.has(id)}>
							<label>
								<input
									type="checkbox"
									checked={!excluded.has(id)}
									aria-label={m.organize_review_item({ index: index + 1, group: group.label })}
									onchange={() => toggle(id)}
								/>
								<img src={thumbnailUrl(id, 'Small')} alt="" loading="lazy" />
							</label>
						</li>
					{/each}
				</ul>
			</section>
		{/each}
	</div>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.organize_cancel()}</button>
		<button
			type="button"
			class="primary"
			disabled={kept === 0 || busy}
			onclick={() => onconfirm(keptIds(groups, excluded), excluded.size > 0)}
		>
			{confirmLabel}
		</button>
	{/snippet}
</Dialog>

<style>
	.lead {
		margin: 0 0 var(--space-3);
		color: var(--color-text-muted);
	}

	.groups {
		display: grid;
		gap: var(--space-4);
		max-height: min(60vh, 560px);
		overflow-y: auto;
		padding-right: var(--space-2);
	}

	.year {
		position: sticky;
		top: 0;
		z-index: 1;
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: var(--space-1) 0;
		background: var(--color-surface-raised);
		font-weight: 600;
		cursor: pointer;
	}

	.count {
		color: var(--color-text-muted);
		font-weight: 400;
		font-size: var(--font-size-sm);
	}

	ul {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
		gap: var(--space-1);
		margin: var(--space-2) 0 0;
		padding: 0;
		list-style: none;
	}

	li label {
		position: relative;
		display: block;
		aspect-ratio: 1;
		border-radius: var(--radius-sm);
		overflow: hidden;
		background: var(--color-placeholder);
		cursor: pointer;
	}

	li img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transition: opacity var(--duration-fast);
	}

	li input {
		position: absolute;
		/* Above the dimmed image, which opacity paints as positioned. */
		z-index: 1;
		top: var(--space-1);
		left: var(--space-1);
		width: 18px;
		height: 18px;
		margin: 0;
		accent-color: var(--color-accent);
	}

	li.out img {
		opacity: 0.3;
		filter: grayscale(1);
	}

	li label:has(input:focus-visible) {
		outline: 2px solid var(--color-focus);
		outline-offset: 1px;
	}
</style>
