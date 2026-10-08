<script lang="ts">
	import { untrack } from 'svelte';
	import { createQuery } from '@tanstack/svelte-query';
	import { apiErrorCode, createAlbum, updateAlbum, type AlbumResponse } from '#lib/api/index.js';
	import { getAlbumRuleOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { isSmart } from './album-list.js';
	import { buildRule, emptyRule, parseRule, type RuleModel } from './smart-rule.js';
	import SmartRuleEditor from './SmartRuleEditor.svelte';

	interface Props {
		/** The album to edit; without it, a new one is created. */
		album?: AlbumResponse;
		/** For a new album: a smart one (filled by a rule) instead of a manual one. */
		smart?: boolean;
		onclose: () => void;
		onsaved: (album: { id: string; name: string }, created: boolean) => void;
	}

	let { album, smart = false, onclose, onsaved }: Props = $props();

	// The dialog is mounted for one album (or one creation) and closed after.
	const initial = untrack(() => ({ album, smart }));
	const editing = !!initial.album;
	const withRule = initial.album ? isSmart(initial.album) : initial.smart;
	// Only the owner may change a smart album's rule; a collaborator renames.
	const editsRule = withRule && (initial.album?.isOwner ?? true);

	let name = $state(initial.album?.name ?? '');
	let description = $state(initial.album?.description ?? '');
	let model = $state<RuleModel>(emptyRule());
	let loadedRule = $state(!(editing && editsRule));
	let saving = $state(false);
	let error = $state<string | null>(null);

	const ruleQuery = createQuery(() => ({
		...getAlbumRuleOptions({ path: { albumId: album?.id ?? '' } }),
		enabled: editing && editsRule
	}));

	// A saved rule fills the editor once.
	$effect(() => {
		if (!loadedRule && ruleQuery.data) {
			model = parseRule(ruleQuery.data.rule);
			loadedRule = true;
		}
	});

	const rule = $derived(editsRule ? buildRule(model) : null);
	const canSave = $derived(!!name.trim() && !saving && loadedRule && (!editsRule || rule !== null));

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (!canSave) return;
		saving = true;
		error = null;
		const body = {
			name: name.trim(),
			description: description.trim() || null,
			smartRule: editsRule ? rule : null
		};
		const result = album
			? await updateAlbum({ path: { albumId: album.id }, body })
			: await createAlbum({ body });
		saving = false;
		if (result.data) {
			onsaved({ id: result.data.id, name: result.data.name }, !album);
			return;
		}
		const code = apiErrorCode(result.error);
		error = code === 'invalid_rule' ? m.albums_error_rule() : m.action_failed();
	}

	const title = editing
		? m.albums_edit_title()
		: withRule
			? m.albums_new_smart_title()
			: m.albums_new_title();
</script>

<Dialog open {title} {onclose} width={editsRule ? '760px' : '480px'}>
	<form id="album-form" class="form" onsubmit={submit}>
		<label class="field">
			<span>{m.albums_field_name()}</span>
			<!-- svelte-ignore a11y_autofocus -->
			<input bind:value={name} required maxlength="200" autofocus />
		</label>
		<label class="field">
			<span>{m.albums_field_description()}</span>
			<textarea bind:value={description} rows="2" maxlength="2000"></textarea>
		</label>

		{#if editsRule}
			<fieldset class="rule">
				<legend>{m.albums_rule_title()}</legend>
				{#if !loadedRule}
					<p class="note" role="status">
						{ruleQuery.isError ? m.albums_error_rule_load() : m.session_restoring()}
					</p>
				{:else}
					<SmartRuleEditor
						bind:model
						people={ruleQuery.data?.people}
						folders={ruleQuery.data?.folders}
					/>
				{/if}
			</fieldset>
		{:else if withRule}
			<p class="note">{m.albums_rule_owner_only()}</p>
		{/if}

		{#if error}<p class="error" role="alert">{error}</p>{/if}
	</form>

	{#snippet actions()}
		<button type="button" onclick={onclose}>{m.dialog_cancel()}</button>
		<button type="submit" form="album-form" class="primary" disabled={!canSave}>
			{editing ? m.albums_save() : m.albums_create()}
		</button>
	{/snippet}
</Dialog>

<style>
	.form {
		display: grid;
		gap: var(--space-4);
	}

	.field {
		display: grid;
		gap: var(--space-1);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.field input,
	.field textarea {
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		font-weight: 400;
		resize: vertical;
	}

	.rule {
		margin: 0;
		padding: var(--space-3) var(--space-4) var(--space-4);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
		/* The dialog's own header, fields and buttons stay in view; the rule scrolls. */
		max-height: max(220px, calc(100vh - 440px));
		overflow-y: auto;
	}

	legend {
		padding: 0 var(--space-1);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.note {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.error {
		margin: 0;
		color: var(--color-danger);
	}
</style>
