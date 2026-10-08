<script lang="ts">
	import { m } from '#lib/paraglide/messages.js';
	import { daysFromNow, localDate, validateLinkForm, type LinkForm } from './share-link.js';

	interface Props {
		form: LinkForm;
		/** Editing a link that already has a password: "keep it" is an option. */
		hadPassword: boolean;
		submitLabel: string;
		busy: boolean;
		onsubmit: () => void;
		oncancel: () => void;
	}

	let { form = $bindable(), hadPassword, submitLabel, busy, onsubmit, oncancel }: Props = $props();

	const id = $props.id();
	let tried = $state(false);
	const error = $derived(validateLinkForm(form));
	const errorText = $derived(
		error === 'max_views'
			? m.albums_link_error_views()
			: error === 'expires_past'
				? m.albums_link_error_past()
				: error === 'password_empty'
					? m.albums_link_error_password()
					: null
	);

	const presets = [
		{ days: 1, label: m.albums_link_expiry_day },
		{ days: 7, label: m.albums_link_expiry_week },
		{ days: 30, label: m.albums_link_expiry_month }
	];

	function submit(event: SubmitEvent) {
		event.preventDefault();
		tried = true;
		if (!error) onsubmit();
	}
</script>

<form class="link-form" onsubmit={submit} novalidate>
	<fieldset>
		<legend>{m.albums_link_expiry()}</legend>
		<div class="row">
			<input
				type="date"
				aria-label={m.albums_link_expiry_date()}
				min={localDate(new Date())}
				bind:value={form.expires}
			/>
			{#each presets as preset (preset.days)}
				<button
					type="button"
					class="chip"
					onclick={() => (form.expires = daysFromNow(preset.days))}
				>
					{preset.label()}
				</button>
			{/each}
			<button type="button" class="chip" onclick={() => (form.expires = '')}>
				{m.albums_link_expiry_never()}
			</button>
		</div>
	</fieldset>

	<fieldset>
		<legend>{m.albums_link_password()}</legend>
		<div class="row">
			{#if hadPassword}
				<label class="check">
					<input type="radio" name="{id}-pw" value="keep" bind:group={form.passwordMode} />
					{m.albums_link_password_keep()}
				</label>
			{/if}
			<label class="check">
				<input type="radio" name="{id}-pw" value="none" bind:group={form.passwordMode} />
				{hadPassword ? m.albums_link_password_remove() : m.albums_link_password_none()}
			</label>
			<label class="check">
				<input type="radio" name="{id}-pw" value="set" bind:group={form.passwordMode} />
				{hadPassword ? m.albums_link_password_change() : m.albums_link_password_set()}
			</label>
		</div>
		{#if form.passwordMode === 'set'}
			<input
				type="password"
				autocomplete="new-password"
				aria-label={m.albums_link_password()}
				placeholder={m.albums_link_password()}
				bind:value={form.password}
			/>
		{/if}
	</fieldset>

	<label class="views">
		<span>{m.albums_link_max_views()}</span>
		<input
			type="text"
			inputmode="numeric"
			placeholder={m.albums_link_unlimited()}
			bind:value={form.maxViews}
		/>
	</label>

	<label class="check">
		<input type="checkbox" bind:checked={form.allowDownload} />
		{m.albums_link_allow_download()}
	</label>
	<label class="check">
		<input type="checkbox" bind:checked={form.allowUpload} />
		<span>
			{m.albums_link_allow_upload()}
			<span class="hint">{m.albums_link_allow_upload_hint()}</span>
		</span>
	</label>

	{#if tried && errorText}<p class="error" role="alert">{errorText}</p>{/if}

	<div class="buttons">
		<button type="button" onclick={oncancel}>{m.dialog_cancel()}</button>
		<button type="submit" class="primary" disabled={busy}>{submitLabel}</button>
	</div>
</form>

<style>
	.link-form {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-surface);
	}

	fieldset {
		display: grid;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		border: 0;
	}

	legend,
	.views > span {
		margin-bottom: var(--space-1);
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.row {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
	}

	.views {
		display: grid;
		max-width: 220px;
	}

	input[type='date'],
	input[type='password'],
	input[type='text'] {
		padding: var(--space-1) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.chip {
		padding: 2px var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: 999px;
		background: var(--color-bg);
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.check {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
		font-size: var(--font-size-sm);
	}

	.hint {
		display: block;
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.error {
		margin: 0;
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}

	.buttons {
		display: flex;
		justify-content: flex-end;
		gap: var(--space-2);
	}

	.buttons button {
		padding: var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		cursor: pointer;
	}

	.buttons .primary {
		border-color: var(--color-accent);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
	}
</style>
