<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import PasswordInput from '#lib/account/settings/PasswordInput.svelte';
	import {
		isStrongPassword,
		passwordRules,
		type PasswordRule
	} from '#lib/account/settings/validation.js';
	import { apiErrorCode, changePassword, deleteMyAccount } from '#lib/api/index.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';

	// --- Change password -------------------------------------------------

	let current = $state('');
	let next = $state('');
	let confirm = $state('');
	let changing = $state(false);
	let changeError = $state<string | null>(null);
	let touchedConfirm = $state(false);

	const rules = $derived(passwordRules(next));
	const ruleText: Record<PasswordRule, () => string> = {
		length: m.settings_password_rule_length,
		upper: m.settings_password_rule_upper,
		lower: m.settings_password_rule_lower,
		digit: m.settings_password_rule_digit,
		symbol: m.settings_password_rule_symbol
	};
	const mismatch = $derived(touchedConfirm && confirm !== '' && confirm !== next);
	const canChange = $derived(
		!changing && current !== '' && isStrongPassword(next) && confirm === next
	);

	async function submitChange(event: SubmitEvent) {
		event.preventDefault();
		touchedConfirm = true;
		if (!canChange) return;
		changing = true;
		changeError = null;
		const { data, error, response } = await changePassword({
			body: { currentPassword: current, newPassword: next }
		});
		changing = false;
		if (data) {
			current = next = confirm = '';
			touchedConfirm = false;
			toasts.show(m.settings_password_changed());
			return;
		}
		const code = apiErrorCode(error);
		changeError =
			code === 'invalid_current_password'
				? m.settings_password_error_current()
				: code === 'invalid_password'
					? m.settings_password_error_weak()
					: response?.status === 403
						? m.settings_error_forbidden()
						: m.action_failed();
	}

	// --- Delete account --------------------------------------------------

	let deleting = $state(false);
	let deletePassword = $state('');
	let deleteError = $state<string | null>(null);
	let busy = $state(false);

	const isPrimaryAdmin = $derived(session.user?.isPrimaryAdmin === true);

	function openDelete() {
		deletePassword = '';
		deleteError = null;
		deleting = true;
	}

	async function confirmDelete(event?: SubmitEvent) {
		event?.preventDefault();
		if (!deletePassword || busy) return;
		busy = true;
		deleteError = null;
		const { error, response } = await deleteMyAccount({ body: { password: deletePassword } });
		busy = false;
		if (response?.ok) {
			deleting = false;
			// The session died with the account; logging out only clears cookies.
			await session.logout().catch(() => {});
			await goto(resolve('/login'), { replace: true });
			toasts.show(m.settings_delete_done());
			return;
		}
		const code = apiErrorCode(error);
		deleteError =
			code === 'invalid_password'
				? m.settings_delete_error_password()
				: code === 'primary_admin_protected'
					? m.settings_delete_primary_admin()
					: response?.status === 403
						? m.settings_error_forbidden()
						: m.action_failed();
	}
</script>

<svelte:head>
	<title>{m.settings_security()} · {m.app_name()}</title>
</svelte:head>

<div class="settings-page">
	<h1>{m.settings_security()}</h1>
	<p class="lead">{m.settings_security_lead()}</p>

	<form class="card" onsubmit={submitChange} novalidate>
		<header>
			<h2>{m.settings_password_title()}</h2>
			<p>{m.settings_password_lead()}</p>
		</header>
		<!-- For password managers: which account this password belongs to. -->
		<input
			type="text"
			class="visually-hidden"
			autocomplete="username"
			value={session.user?.username ?? ''}
			readonly
			tabindex="-1"
			aria-hidden="true"
		/>
		<div class="fields">
			<label class="field">
				<span>{m.settings_password_current()}</span>
				<PasswordInput autocomplete="current-password" required bind:value={current} />
			</label>
		</div>
		<div class="fields">
			<label class="field">
				<span>{m.settings_password_new()}</span>
				<PasswordInput
					autocomplete="new-password"
					required
					aria-describedby="password-rules"
					bind:value={next}
				/>
			</label>
			<label class="field">
				<span>{m.settings_password_confirm()}</span>
				<PasswordInput
					autocomplete="new-password"
					required
					aria-invalid={mismatch}
					aria-describedby={mismatch ? 'password-mismatch' : undefined}
					onblur={() => (touchedConfirm = true)}
					bind:value={confirm}
				/>
				{#if mismatch}
					<span id="password-mismatch" class="error">{m.settings_password_mismatch()}</span>
				{/if}
			</label>
		</div>
		<ul id="password-rules" class="rules" aria-label={m.settings_password_rules()}>
			{#each Object.entries(rules) as [rule, ok] (rule)}
				<li class:ok>
					<Icon name={ok ? 'check' : 'close'} size={16} />
					{ruleText[rule as PasswordRule]()}
					<span class="visually-hidden">
						{ok ? m.settings_password_rule_met() : m.settings_password_rule_unmet()}
					</span>
				</li>
			{/each}
		</ul>
		{#if changeError}
			<p class="error" role="alert">{changeError}</p>
		{/if}
		<div class="actions">
			<button type="submit" class="button primary" disabled={!canChange}>
				<Icon name="lock" size={18} />
				{changing ? m.settings_saving() : m.settings_password_submit()}
			</button>
		</div>
	</form>

	<section class="card danger" aria-labelledby="delete-title">
		<header>
			<h2 id="delete-title">{m.settings_delete_title()}</h2>
			<p>{m.settings_delete_lead()}</p>
		</header>
		{#if isPrimaryAdmin}
			<p class="hint">{m.settings_delete_primary_admin()}</p>
		{/if}
		<div class="actions">
			<button type="button" class="button danger" disabled={isPrimaryAdmin} onclick={openDelete}>
				<Icon name="delete" size={18} />
				{m.settings_delete_open()}
			</button>
		</div>
	</section>
</div>

<Dialog open={deleting} title={m.settings_delete_title()} onclose={() => (deleting = false)}>
	<form id="delete-form" class="delete" onsubmit={confirmDelete}>
		<p>{m.settings_delete_warning()}</p>
		<label class="field">
			<span>{m.settings_delete_password()}</span>
			<PasswordInput
				autocomplete="current-password"
				required
				aria-invalid={deleteError !== null}
				bind:value={deletePassword}
			/>
		</label>
		{#if deleteError}
			<p class="error" role="alert">{deleteError}</p>
		{/if}
	</form>
	{#snippet actions()}
		<button type="button" onclick={() => (deleting = false)}>{m.dialog_cancel()}</button>
		<button type="submit" form="delete-form" class="danger" disabled={!deletePassword || busy}>
			{m.settings_delete_confirm()}
		</button>
	{/snippet}
</Dialog>

<style>
	.rules {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2) var(--space-4);
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.rules li {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
	}

	.rules li.ok {
		color: var(--color-accent);
	}

	.delete {
		display: grid;
		gap: var(--space-3);
	}

	.delete p {
		margin: 0;
	}

	.delete .field {
		display: grid;
		gap: var(--space-1);
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.delete .error {
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}
</style>
