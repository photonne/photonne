<script lang="ts">
	import Logo from '#lib/components/Logo.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { session, type LoginFailure } from '#lib/auth/session.svelte.js';
	import { safeReturnTo } from '#lib/navigation/return-to.js';
	import { m } from '#lib/paraglide/messages.js';

	let username = $state('');
	let password = $state('');
	let submitting = $state(false);
	let failure = $state<LoginFailure | null>(null);

	const returnTo = $derived(safeReturnTo(page.url.searchParams.get('returnTo')));

	const failureText: Record<LoginFailure, () => string> = {
		invalidCredentials: m.login_error_invalid,
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
		failure = await session.login(username.trim(), password);
		submitting = false;
		if (failure === 'invalidCredentials') password = '';
	}
</script>

<svelte:head>
	<title>{m.login_title()} · {m.app_name()}</title>
</svelte:head>

<main class="page">
	<form class="card" onsubmit={submit} aria-describedby={failure ? 'login-error' : undefined}>
		<h1><Logo size={40} /></h1>
		<p class="subtitle">{m.login_title()}</p>

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
			<input
				name="password"
				type="password"
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
	</form>
</main>

<style>
	.page {
		min-height: 100vh;
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

	input {
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
		font-size: var(--font-size-md);
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
