<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { beforeNavigate, goto } from '$app/navigation';
	import { getDemoInfoOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import NightlyPanel from './NightlyPanel.svelte';
	import NotificationsPanel from './NotificationsPanel.svelte';
	import PerformancePanel from './PerformancePanel.svelte';
	import SettingField from './SettingField.svelte';
	import TrashPanel from './TrashPanel.svelte';
	import { icons } from './icons.js';
	import { SettingsForm } from './settings-form.svelte.js';
	import { toStored } from './settings-model.js';
	import { isVisible, type SettingsSection } from './settings-sections.js';

	let { section }: { section: SettingsSection } = $props();

	const queryClient = useQueryClient();
	// The section never changes for one editor: the page keys it by section.
	// svelte-ignore state_referenced_locally
	const form = new SettingsForm(section, queryClient);
	form.load();

	// The public demo keeps server settings read-only (the server refuses the save).
	const demo = createQuery(() => getDemoInfoOptions());
	const readOnly = $derived(demo.data?.enabled === true);

	const modelChanged = $derived(
		section.id === 'embedding' &&
			form.status === 'ready' &&
			form.values['Embedding.ModelVersion']?.trim() !== form.original['Embedding.ModelVersion']
	);

	async function save(event?: Event) {
		event?.preventDefault();
		if (!form.canSave || readOnly) return;
		const { saved, failed } = await form.save();
		if (failed.length) toasts.error(m.ops_set_save_failed({ count: failed.length }));
		else if (saved) toasts.show(m.ops_set_saved());
	}

	function onkeydown(event: KeyboardEvent) {
		if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
			event.preventDefault();
			save();
		}
	}

	// Unsaved edits aren't lost on the way out without a word.
	let leaving = $state<URL | null>(null);
	let allowLeave = false;
	beforeNavigate((navigation) => {
		if (allowLeave || !form.dirty || form.saving) return;
		navigation.cancel();
		// Closing the tab: the browser asks on its own once cancelled.
		if (!navigation.willUnload && navigation.to) leaving = navigation.to.url;
	});

	function discardAndLeave() {
		const target = leaving;
		leaving = null;
		if (!target) return;
		allowLeave = true;
		goto(target.pathname + target.search + target.hash);
	}

	function changed(key: string) {
		const field = form.fields.find((f) => f.key === key);
		return (
			field !== undefined &&
			toStored(field, form.values[key] ?? '') !== toStored(field, form.original[key] ?? '')
		);
	}
</script>

<svelte:window {onkeydown} />

<div class="editor">
	<header class="ops-head">
		<div>
			<h1>{section.title()}</h1>
			<p>{section.description()}</p>
		</div>
		{#if section.maintenanceHint}
			<div class="ops-actions">
				<a class="ops-btn" href={appHref('/admin/maintenance')}>
					<Icon name="build" size={18} />
					{m.ops_set_open_maintenance()}
				</a>
			</div>
		{/if}
	</header>

	{#if readOnly}
		<p class="ops-alert warn"><Icon name="lock" size={18} />{m.ops_set_demo()}</p>
	{/if}

	{#if form.status === 'loading'}
		<p class="ops-muted">{m.ops_loading()}</p>
	{:else if form.status === 'error'}
		<p class="ops-alert error" role="alert">
			<Icon path={icons.warning} size={18} />
			{m.ops_set_load_failed()}
			<button type="button" class="ops-btn sm" onclick={() => form.load()}>{m.ops_retry()}</button>
		</p>
	{:else}
		<div
			class="layout"
			class:with-panel={section.panel !== undefined && section.panel !== 'embedding'}
		>
			<form id="settings-form" class="groups" onsubmit={save} novalidate>
				{#each section.groups as group, index (index)}
					{@const fields = group.fields.filter((field) => isVisible(field, form.values))}
					<section
						class="ops-card group"
						aria-labelledby={group.title ? `group-${index}` : undefined}
					>
						{#if group.title}
							<header>
								<h2 id="group-{index}">{group.title()}</h2>
								{#if group.description}<p>{group.description()}</p>{/if}
							</header>
						{/if}
						<div class="fields">
							{#each fields as field (field.key)}
								<SettingField
									{field}
									value={form.values[field.key] ?? ''}
									error={form.errors[field.key]}
									changed={changed(field.key)}
									disabled={readOnly || form.saving}
									onchange={(value) => form.set(field.key, value)}
								/>
								{#if field.key === 'Embedding.ModelVersion' && modelChanged}
									<p class="ops-alert warn">
										<Icon path={icons.warning} size={18} />{m.ops_set_embedding_model_warning()}
									</p>
								{/if}
							{/each}
						</div>
					</section>
				{/each}
			</form>

			{#if section.panel === 'trash'}
				<aside><TrashPanel {readOnly} /></aside>
			{:else if section.panel === 'notifications'}
				<aside><NotificationsPanel {readOnly} /></aside>
			{:else if section.panel === 'nightly'}
				<aside><NightlyPanel {readOnly} /></aside>
			{:else if section.panel === 'performance'}
				<aside><PerformancePanel /></aside>
			{/if}
		</div>

		<div class="save-bar" role="region" aria-label={m.ops_set_save_region()}>
			<p class="state" aria-live="polite">
				{#if form.saving}
					{m.ops_set_saving()}
				{:else if !form.valid}
					<span class="invalid">{m.ops_set_invalid()}</span>
				{:else if form.dirty}
					{m.ops_set_pending({ count: form.pending.length })}
				{:else}
					<span class="ops-muted">{m.ops_set_clean()}</span>
				{/if}
			</p>
			<span class="ops-muted ops-small shortcut">{m.ops_set_shortcut()}</span>
			<button
				type="button"
				class="ops-btn"
				disabled={!form.dirty || form.saving}
				onclick={() => form.reset()}>{m.ops_set_discard()}</button
			>
			<button
				type="submit"
				form="settings-form"
				class="ops-btn primary"
				disabled={!form.canSave || readOnly}>{m.ops_set_save()}</button
			>
		</div>
	{/if}
</div>

<Dialog open={leaving !== null} title={m.ops_set_leave_title()} onclose={() => (leaving = null)}>
	<p class="leave">{m.ops_set_leave_text()}</p>
	{#snippet actions()}
		<button type="button" onclick={() => (leaving = null)}>{m.ops_set_keep_editing()}</button>
		<button type="button" class="danger" onclick={discardAndLeave}>{m.ops_set_discard()}</button>
	{/snippet}
</Dialog>

<style>
	.editor {
		display: grid;
		gap: var(--space-4);
		max-width: 1180px;
		padding: var(--space-6) var(--space-6) 0;
	}

	.layout {
		display: grid;
		gap: var(--space-4);
		align-items: start;
	}

	.layout.with-panel {
		grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
	}

	aside {
		position: sticky;
		top: calc(57px + var(--space-4));
	}

	.groups {
		display: grid;
		gap: var(--space-4);
	}

	.group {
		padding: var(--space-2) var(--space-4);
	}

	.group > header {
		padding: var(--space-3) 0 var(--space-1);
	}

	.group h2 {
		margin: 0;
		font-size: var(--font-size-md);
	}

	.group > header p {
		margin: 2px 0 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.fields > :global(.field + .field) {
		border-top: 1px solid var(--color-border);
	}

	.save-bar {
		position: sticky;
		bottom: 0;
		z-index: 1;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-3);
		margin: 0 calc(-1 * var(--space-6));
		padding: var(--space-3) var(--space-6);
		border-top: 1px solid var(--color-border);
		background: var(--color-bg);
	}

	.state {
		flex: 1;
		margin: 0;
		font-size: var(--font-size-sm);
		font-weight: 500;
	}

	.invalid {
		color: var(--color-danger);
	}

	.leave {
		margin: 0;
		color: var(--color-text-muted);
	}

	@media (max-width: 1200px) {
		.layout.with-panel {
			grid-template-columns: minmax(0, 1fr);
		}

		aside {
			position: static;
		}
	}

	@media (max-width: 720px) {
		.shortcut {
			display: none;
		}
	}
</style>
