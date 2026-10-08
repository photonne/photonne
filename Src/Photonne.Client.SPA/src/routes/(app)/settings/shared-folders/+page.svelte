<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { sharedFolderRows } from '#lib/account/settings/shared-folders.js';
	import { setFolderDiscoveryVisibility } from '#lib/api/index.js';
	import { getAllFoldersOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';

	const queryClient = useQueryClient();
	const folders = createQuery(() => getAllFoldersOptions());

	// Pending choices shown at once, before the server answers.
	let overrides = $state<Record<string, boolean>>({});

	const rows = $derived(sharedFolderRows(folders.data ?? []));
	const hiddenCount = $derived(rows.filter((row) => overrides[row.id] ?? row.excluded).length);

	async function setIncluded(id: string, name: string, included: boolean, undo = true) {
		overrides[id] = !included;
		const { data } = await setFolderDiscoveryVisibility({
			path: { folderId: id },
			body: { included }
		});
		if (!data) {
			delete overrides[id];
			toasts.error(m.action_failed());
			return;
		}
		// The timeline, memories, people and search all change with it.
		await queryClient.invalidateQueries();
		delete overrides[id];
		if (undo) {
			toasts.show(
				included ? m.settings_discovery_shown({ name }) : m.settings_discovery_hidden({ name }),
				{
					action: {
						label: m.action_undo(),
						run: () => setIncluded(id, name, !included, false)
					}
				}
			);
		}
	}
</script>

<svelte:head>
	<title>{m.settings_discovery()} · {m.app_name()}</title>
</svelte:head>

<div class="settings-page">
	<h1>{m.settings_discovery()}</h1>
	<p class="lead">{m.settings_discovery_lead()}</p>

	<section class="card" aria-labelledby="shared-title">
		<header>
			<h2 id="shared-title">{m.settings_discovery_folders()}</h2>
			<p>
				{rows.length === 0
					? m.settings_discovery_folders_lead()
					: m.settings_discovery_hidden_count({ count: hiddenCount })}
			</p>
		</header>

		{#if folders.isPending}
			<p class="hint" role="status">{m.session_restoring()}</p>
		{:else if folders.isError}
			<p class="error" role="alert">{m.error_loading()}</p>
		{:else if rows.length === 0}
			<p class="hint">{m.settings_discovery_empty()}</p>
		{:else}
			<ul class="folders">
				{#each rows as row (row.id)}
					{@const excluded = overrides[row.id] ?? row.excluded}
					<li style:--depth={row.depth} class:excluded>
						<label>
							<input
								type="checkbox"
								role="switch"
								checked={!excluded}
								onchange={(event) => setIncluded(row.id, row.name, event.currentTarget.checked)}
							/>
							<Icon name="folder" size={20} />
							<span class="name">
								{row.name}
								<small>{m.settings_discovery_items({ count: row.assetCount })}</small>
							</span>
							<span class="state">
								{excluded
									? m.settings_discovery_state_hidden()
									: m.settings_discovery_state_shown()}
							</span>
						</label>
					</li>
				{/each}
			</ul>
		{/if}
	</section>
</div>

<style>
	.folders {
		list-style: none;
		margin: 0;
		padding: 0;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		overflow: hidden;
	}

	li + li {
		border-top: 1px solid var(--color-border);
	}

	label {
		display: flex;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-2) var(--space-4);
		padding-left: calc(var(--space-4) + var(--depth) * var(--space-6));
		cursor: pointer;
	}

	label:hover {
		background: var(--color-surface);
	}

	label:has(input:focus-visible) {
		outline: 2px solid var(--color-focus);
		outline-offset: -2px;
	}

	input {
		width: 18px;
		height: 18px;
		accent-color: var(--color-accent);
	}

	.name {
		flex: 1;
		display: grid;
		min-width: 0;
	}

	small,
	.state {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.excluded .name {
		color: var(--color-text-muted);
	}

	li :global(svg) {
		color: var(--color-text-muted);
	}
</style>
