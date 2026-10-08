<script lang="ts">
	import { createQuery, keepPreviousData, useQueryClient } from '@tanstack/svelte-query';
	import {
		apiErrorCode,
		moveOrganizeRule,
		previewOrganizeRule,
		reviewOrganizeRule,
		type FolderResponse,
		type SmartRuleNode
	} from '#lib/api/index.js';
	import { getFolderTreeOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { thumbnailUrl } from '#lib/media.js';
	import { m } from '#lib/paraglide/messages.js';
	import OrganizeNav from '#lib/search/organize/OrganizeNav.svelte';
	import { movedMessage, moveToFolder, refreshOrganize } from '#lib/search/organize/organize.js';
	import ReviewMoveDialog from '#lib/search/organize/ReviewMoveDialog.svelte';
	import { reviewGroups, type ReviewGroup } from '#lib/search/organize/review.js';
	import RuleEditor from '#lib/search/organize/RuleEditor.svelte';
	import { buildRule, type Condition } from '#lib/search/organize/rule-model.js';

	const queryClient = useQueryClient();
	const PREVIEW_KEY = 'organize-rule-preview';
	const SAMPLE = 24;

	let op = $state<'AND' | 'OR'>('AND');
	let conditions = $state<Condition[]>([]);
	let targetId = $state('');
	let byYear = $state(true);
	let busy = $state(false);
	let reviewing = $state<ReviewGroup[] | null>(null);

	const rule = $derived(buildRule(op, conditions));

	// The preview follows the rule a moment after the last change.
	let previewed = $state<SmartRuleNode | null>(null);
	$effect(() => {
		const next = rule;
		const timer = setTimeout(() => (previewed = next), 350);
		return () => clearTimeout(timer);
	});

	const preview = createQuery(() => ({
		queryKey: [PREVIEW_KEY, previewed],
		queryFn: async () => {
			const { data, error } = await previewOrganizeRule({
				body: { rule: previewed, sampleSize: SAMPLE }
			});
			if (error || !data) throw error;
			return data;
		},
		enabled: previewed !== null,
		placeholderData: keepPreviousData
	}));

	const folders = createQuery(() => getFolderTreeOptions());

	function writable(
		nodes: readonly FolderResponse[],
		depth = 0
	): { folder: FolderResponse; depth: number }[] {
		return nodes.flatMap((folder) => [
			...(folder.canWrite ? [{ folder, depth }] : []),
			...writable(folder.subFolders ?? [], depth + 1)
		]);
	}

	const targets = $derived(writable(folders.data ?? []));
	const target = $derived(targets.find((t) => t.folder.id === targetId)?.folder ?? null);
	const count = $derived(rule && previewed ? (preview.data?.count ?? null) : null);
	const canMove = $derived(!!rule && !!target && (count ?? 0) > 0 && !busy);

	async function run(action: () => Promise<void>) {
		if (busy) return;
		busy = true;
		try {
			await action();
		} catch (error) {
			toasts.error(
				apiErrorCode(error) === 'invalid_rule' ? m.organize_rule_invalid() : m.organize_failed()
			);
		} finally {
			busy = false;
		}
	}

	function openReview() {
		if (!rule) return;
		const current = rule;
		return run(async () => {
			const { data, error } = await reviewOrganizeRule({ body: { rule: current } });
			if (error || !data) throw error;
			const groups = reviewGroups(data.groups);
			if (groups.length === 0) {
				toasts.show(m.organize_rule_nothing());
				queryClient.invalidateQueries({ queryKey: [PREVIEW_KEY] });
				return;
			}
			reviewing = groups;
		});
	}

	/**
	 * Untouched, the server resolves the rule again (thousands of photos don't
	 * travel as ids); with photos taken out, exactly the kept ids move.
	 */
	function move(kept: string[], edited: boolean) {
		reviewing = null;
		if (!rule || !target) return;
		const folder = target;
		const current = rule;
		return run(async () => {
			let result;
			if (edited) {
				result = await moveToFolder(kept, folder, byYear);
			} else {
				const { data, error } = await moveOrganizeRule({
					body: { rule: current, targetFolderId: folder.id, organizeByCaptureYear: byYear }
				});
				if (error || !data) throw error;
				result = data;
			}
			refreshOrganize(queryClient);
			queryClient.invalidateQueries({ queryKey: [PREVIEW_KEY] });
			toasts.show(movedMessage(result, folder.name));
		});
	}
</script>

<svelte:head>
	<title>{m.organize_tab_rules()} · {m.nav_organize()} · {m.app_name()}</title>
</svelte:head>

<div class="page">
	<h1>{m.nav_organize()}</h1>
	<OrganizeNav />

	<div class="layout">
		<section class="rule" aria-labelledby="rule-title">
			<h2 id="rule-title">{m.organize_rule_title()}</h2>
			<p class="hint">{m.organize_rule_hint()}</p>
			<RuleEditor bind:op bind:conditions />
		</section>

		<aside class="side" aria-labelledby="preview-title">
			<h2 id="preview-title">{m.organize_rule_preview()}</h2>
			<p class="count" aria-live="polite">
				{#if !rule}
					{m.organize_rule_preview_empty()}
				{:else if count === null}
					{m.search_loading()}
				{:else}
					{m.organize_rule_matches({ count })}
				{/if}
			</p>
			{#if rule && preview.data && count}
				{#if preview.data.yearBreakdown.length}
					<ul class="years" aria-label={m.organize_rule_years()}>
						{#each [...preview.data.yearBreakdown].sort((a, b) => b.year - a.year) as year (year.year)}
							<li>{year.year} <span>{year.count}</span></li>
						{/each}
					</ul>
				{/if}
				<ul class="sample" aria-label={m.organize_rule_sample()}>
					{#each preview.data.sampleAssetIds as id (id)}
						<li><img src={thumbnailUrl(id, 'Small')} alt="" loading="lazy" /></li>
					{/each}
				</ul>
			{/if}

			<h2>{m.organize_rule_destination()}</h2>
			<label class="field">
				{m.organize_rule_folder_target()}
				<select bind:value={targetId}>
					<option value="">{m.organize_rule_folder_choose()}</option>
					{#each targets as { folder, depth } (folder.id)}
						<option value={folder.id}>{' '.repeat(depth)}{folder.name}</option>
					{/each}
				</select>
			</label>
			<label class="check">
				<input type="checkbox" bind:checked={byYear} />
				{m.organize_rule_by_year()}
			</label>
			<button type="button" class="primary" disabled={!canMove} onclick={openReview}>
				{m.organize_review_and_move()}
			</button>
		</aside>
	</div>
</div>

<ReviewMoveDialog
	open={reviewing !== null}
	title={m.organize_rule_review_title({ folder: target?.name ?? '' })}
	groups={reviewing ?? []}
	confirmLabel={m.organize_move()}
	{busy}
	onclose={() => (reviewing = null)}
	onconfirm={move}
/>

<style>
	.page {
		padding-bottom: var(--space-8);
	}

	h1 {
		margin: 0;
		padding: var(--space-4) var(--space-4) 0;
		font-size: var(--font-size-xl);
	}

	h2 {
		margin: 0;
		font-size: var(--font-size-md);
	}

	.layout {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 340px;
		align-items: start;
		gap: var(--space-6);
		padding: var(--space-2) var(--space-4);
	}

	@media (max-width: 1100px) {
		.layout {
			grid-template-columns: minmax(0, 1fr);
		}
	}

	.rule,
	.side {
		display: grid;
		gap: var(--space-3);
	}

	.side {
		position: sticky;
		top: var(--space-4);
		padding: var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
	}

	.hint {
		margin: 0;
		color: var(--color-text-muted);
	}

	.count {
		margin: 0;
		font-size: var(--font-size-lg);
		font-weight: 600;
	}

	.years,
	.sample {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.years li {
		padding: 2px var(--space-2);
		border-radius: 999px;
		background: var(--color-surface-raised);
		font-size: var(--font-size-sm);
	}

	.years span {
		color: var(--color-text-muted);
	}

	.sample {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(44px, 64px));
	}

	.sample img {
		display: block;
		width: 100%;
		aspect-ratio: 1;
		object-fit: cover;
		border-radius: 4px;
		background: var(--color-placeholder);
	}

	.field {
		display: grid;
		gap: var(--space-1);
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	select {
		padding: var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		color: var(--color-text);
	}

	.check {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.primary {
		padding: var(--space-2) var(--space-4);
		border: 1px solid var(--color-accent);
		border-radius: var(--radius-sm);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
		cursor: pointer;
	}

	.primary:disabled {
		opacity: 0.5;
		cursor: default;
	}
</style>
