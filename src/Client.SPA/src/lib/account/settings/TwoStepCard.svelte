<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { renderSVG } from 'uqr';
	import { apiErrorCode, disableTotp, enableTotp, setupTotp } from '#lib/api/index.js';
	import {
		getTotpStatusOptions,
		getTotpStatusQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import PasswordInput from './PasswordInput.svelte';

	/**
	 * Two-step verification: off → a QR for the authenticator app and its
	 * first code → on, with the recovery codes shown once. Turning it off
	 * asks for the password.
	 */
	const queryClient = useQueryClient();
	const status = createQuery(() => getTotpStatusOptions());

	let setup = $state<{ secret: string; qr: string } | null>(null);
	let code = $state('');
	let busy = $state(false);
	let error = $state<string | null>(null);
	let recoveryCodes = $state<string[] | null>(null);
	let disabling = $state(false);
	let password = $state('');

	function refresh() {
		return queryClient.invalidateQueries({ queryKey: getTotpStatusQueryKey() });
	}

	async function start() {
		busy = true;
		error = null;
		const { data, response } = await setupTotp();
		busy = false;
		if (!data) {
			error = response?.status === 403 ? m.settings_error_forbidden() : m.action_failed();
			return;
		}
		code = '';
		setup = { secret: data.secret, qr: renderSVG(data.otpAuthUri, { ecc: 'M', border: 2 }) };
	}

	async function confirm(event: SubmitEvent) {
		event.preventDefault();
		if (busy || !code.trim()) return;
		busy = true;
		error = null;
		const { data, error: failure } = await enableTotp({ body: { code: code.trim() } });
		busy = false;
		if (!data) {
			error =
				apiErrorCode(failure) === 'totp_invalid' ? m.settings_totp_error_code() : m.action_failed();
			return;
		}
		setup = null;
		recoveryCodes = data.recoveryCodes;
		await refresh();
	}

	async function copyCodes() {
		try {
			await navigator.clipboard.writeText(recoveryCodes?.join('\n') ?? '');
			toasts.show(m.settings_totp_codes_copied());
		} catch {
			toasts.error(m.action_failed());
		}
	}

	async function turnOff(event: SubmitEvent) {
		event.preventDefault();
		if (busy || !password) return;
		busy = true;
		error = null;
		const { response, error: failure } = await disableTotp({ body: { password } });
		busy = false;
		if (!response?.ok) {
			error =
				apiErrorCode(failure) === 'invalid_current_password'
					? m.settings_password_error_current()
					: m.action_failed();
			return;
		}
		disabling = false;
		password = '';
		toasts.show(m.settings_totp_disabled());
		await refresh();
	}
</script>

<section class="card" aria-labelledby="totp-title">
	<header>
		<h2 id="totp-title">{m.settings_totp_title()}</h2>
		<p>{m.settings_totp_lead()}</p>
	</header>

	{#if recoveryCodes}
		<div class="codes" role="status">
			<p><strong>{m.settings_totp_codes_title()}</strong> {m.settings_totp_codes_lead()}</p>
			<ul aria-label={m.settings_totp_codes_title()}>
				{#each recoveryCodes as recovery (recovery)}
					<li><code>{recovery}</code></li>
				{/each}
			</ul>
			<div class="actions">
				<button type="button" class="btn" onclick={copyCodes}>{m.settings_totp_codes_copy()}</button
				>
				<button type="button" class="btn primary" onclick={() => (recoveryCodes = null)}>
					{m.settings_totp_codes_done()}
				</button>
			</div>
		</div>
	{:else if status.data?.enabled}
		<p class="on">
			<Icon name="check" size={18} />
			{m.settings_totp_on({ count: status.data.recoveryCodesLeft })}
		</p>
		<div class="actions">
			<button
				type="button"
				class="btn outline-danger"
				onclick={() => {
					error = null;
					disabling = true;
				}}>{m.settings_totp_disable()}</button
			>
		</div>
	{:else if setup}
		<form class="setup" onsubmit={confirm}>
			<!-- Generated here from the server's otpauth:// link: no user content in it. -->
			<figure class="qr" aria-label={m.settings_totp_qr_label()}>
				<!-- eslint-disable-next-line svelte/no-at-html-tags -->
				{@html setup.qr}
			</figure>
			<div class="steps">
				<p>{m.settings_totp_scan()}</p>
				<p class="secret">
					{m.settings_totp_secret()} <code>{setup.secret}</code>
				</p>
				<label class="field">
					<span>{m.settings_totp_code()}</span>
					<input
						autocomplete="one-time-code"
						inputmode="numeric"
						maxlength="8"
						required
						bind:value={code}
					/>
				</label>
				{#if error}<p class="error" role="alert">{error}</p>{/if}
				<div class="actions">
					<button type="button" class="btn" disabled={busy} onclick={() => (setup = null)}>
						{m.dialog_cancel()}
					</button>
					<button type="submit" class="btn primary" disabled={busy || !code.trim()}>
						{m.settings_totp_confirm()}
					</button>
				</div>
			</div>
		</form>
	{:else}
		{#if error}<p class="error" role="alert">{error}</p>{/if}
		<div class="actions">
			<button type="button" class="btn primary" disabled={busy || status.isPending} onclick={start}>
				<Icon name="lock" size={18} />
				{m.settings_totp_enable()}
			</button>
		</div>
	{/if}
</section>

<Dialog
	open={disabling}
	title={m.settings_totp_disable()}
	onclose={() => !busy && (disabling = false)}
>
	<form id="totp-off" class="off" onsubmit={turnOff}>
		<p>{m.settings_totp_disable_warning()}</p>
		<label class="field">
			<span>{m.settings_password_current()}</span>
			<PasswordInput autocomplete="current-password" required bind:value={password} />
		</label>
		{#if error}<p class="error" role="alert">{error}</p>{/if}
	</form>
	{#snippet actions()}
		<button type="button" disabled={busy} onclick={() => (disabling = false)}
			>{m.dialog_cancel()}</button
		>
		<button type="submit" form="totp-off" class="danger" disabled={!password || busy}>
			{m.settings_totp_disable()}
		</button>
	{/snippet}
</Dialog>

<style>
	.setup {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-5);
		align-items: flex-start;
	}

	.qr {
		width: 180px;
		margin: 0;
		padding: var(--space-2);
		border-radius: var(--radius-md);
		/* QR readers want dark on light, whatever the theme. */
		background: #fff;
		color: #000;
	}

	.qr :global(svg) {
		display: block;
		width: 100%;
		height: auto;
	}

	.steps {
		flex: 1;
		min-width: 220px;
		display: grid;
		gap: var(--space-3);
	}

	.steps p,
	.codes p,
	.off p {
		margin: 0;
	}

	.secret {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	code {
		overflow-wrap: anywhere;
	}

	.codes ul {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
		gap: var(--space-2);
		margin: var(--space-3) 0;
		padding: 0;
		list-style: none;
		font-size: var(--font-size-md);
	}

	.on {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		margin: 0;
		color: var(--color-success);
	}

	.off {
		display: grid;
		gap: var(--space-3);
	}

	.error {
		margin: 0;
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}
</style>
