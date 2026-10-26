<script lang="ts">
	import PasswordInput from '#lib/account/settings/PasswordInput.svelte';
	import Logo from '#lib/components/Logo.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { session, type LoginFailure } from '#lib/auth/session.svelte.js';
	import { safeReturnTo } from '#lib/navigation/return-to.js';
	import { m } from '#lib/paraglide/messages.js';

	let username = $state('');
	let password = $state('');
	let totpCode = $state('');
	/** The password was right; the account wants its two-step code too. */
	let askingCode = $state(false);
	let submitting = $state(false);
	let failure = $state<LoginFailure | null>(null);

	const returnTo = $derived(safeReturnTo(page.url.searchParams.get('returnTo')));

	const failureText: Record<LoginFailure, () => string> = {
		invalidCredentials: m.login_error_invalid,
		totpRequired: m.login_totp_hint,
		totpInvalid: m.login_error_totp,
		rateLimited: m.login_error_rate_limited,
		network: m.login_error_network,
		server: m.login_error_server
	};

	$effect(() => {
		if (session.status === 'signedIn') goto(returnTo, { replace: true });
	});

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		submitting = true;
		failure = await session.login(username.trim(), password, askingCode ? totpCode : undefined);
		submitting = false;
		if (failure === 'invalidCredentials') {
			password = '';
			askingCode = false;
		}
		if (failure === 'totpRequired') askingCode = true;
		if (failure === 'totpInvalid') totpCode = '';
	}

	function backToPassword() {
		askingCode = false;
		totpCode = '';
		password = '';
		failure = null;
	}
</script>

<svelte:head>
	<title>{m.login_title()} · {m.app_name()}</title>
</svelte:head>

<main class="page">
	<form class="card" onsubmit={submit} aria-describedby={failure ? 'login-error' : undefined}>
		<h1><Logo size={40} /></h1>
		<p class="subtitle">{m.login_title()}</p>

		{#if askingCode}
			<!-- Second step: the code from the authenticator app (or a recovery code). -->
			<label>
				<span>{m.login_totp_code()}</span>
				<!-- svelte-ignore a11y_autofocus -->
				<input
					name="totp"
					autocomplete="one-time-code"
					inputmode="text"
					autocapitalize="none"
					spellcheck="false"
					maxlength="20"
					required
					autofocus
					bind:value={totpCode}
				/>
			</label>
			{#if failure === 'totpInvalid'}
				<p id="login-error" class="error" role="alert">{failureText[failure]()}</p>
			{:else}
				<p class="hint">{m.login_totp_hint()}</p>
			{/if}
			<button type="submit" disabled={submitting}>
				{submitting ? m.login_submitting() : m.login_totp_submit()}
			</button>
			<button type="button" class="link" onclick={backToPassword}>{m.login_totp_back()}</button>
		{:else}
			<label>
				<span>{m.login_username()}</span>
				<!-- svelte-ignore a11y_autofocus -->
				<input
					name="username"
					autocomplete="username"
					autocapitalize="none"
					spellcheck="false"
					required
					autofocus
					bind:value={username}
				/>
			</label>

			<label>
				<span>{m.login_password()}</span>
				<PasswordInput
					name="password"
					autocomplete="current-password"
					required
					bind:value={password}
				/>
			</label>

			{#if failure}
				<p id="login-error" class="error" role="alert">{failureText[failure]()}</p>
			{/if}

			<button type="submit" disabled={submitting}>
				{submitting ? m.login_submitting() : m.login_submit()}
			</button>
		{/if}
	</form>
</main>

<style>
	.page {
		min-height: 100dvh;
		display: grid;
		place-items: center;
		padding: var(--space-6);
		background:
			radial-gradient(60rem 30rem at 50% -10%, var(--color-accent-soft), transparent 70%),
			var(--color-surface);
	}

	.card {
		width: min(100%, 360px);
		display: grid;
		gap: var(--space-4);
		padding: var(--space-8) var(--space-6);
		background: var(--color-surface-raised);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-raised);
	}

	h1 {
		margin: 0 0 var(--space-2);
		display: flex;
		justify-content: center;
	}

	.subtitle {
		margin: calc(-1 * var(--space-3)) 0 0;
		text-align: center;
		color: var(--color-text-muted);
	}

	label {
		display: grid;
		gap: var(--space-1);
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	/* The shared fields (ui.css), a size up; the password's is PasswordInput's. */
	label :global(input) {
		min-height: var(--control-h-lg);
		font-size: var(--font-size-md);
	}

	.hint {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	button.link {
		padding: 0;
		background: none;
		color: var(--color-accent);
		font-weight: 500;
	}

	.error {
		margin: 0;
		color: var(--color-danger);
		font-size: var(--font-size-sm);
	}

	button {
		padding: var(--space-3);
		border: none;
		border-radius: var(--radius-sm);
		background: var(--color-accent);
		color: var(--color-accent-text);
		font-weight: 600;
		cursor: pointer;
	}

	button:disabled {
		opacity: 0.7;
		cursor: progress;
	}
</style>
