<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import { isValidEmail, isValidUsername } from '#lib/account/settings/validation.js';
	import {
		apiErrorCode,
		previewMyRename,
		updateProfile,
		type RenamePreviewDto,
		type UserDto
	} from '#lib/api/index.js';
	import { getCurrentUserOptions } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import { session } from '#lib/auth/session.svelte.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';

	const queryClient = useQueryClient();
	const me = createQuery(() => getCurrentUserOptions());

	let username = $state('');
	let email = $state('');
	let firstName = $state('');
	let lastName = $state('');
	let loadedFor = $state<string | null>(null);
	let saving = $state(false);
	let serverError = $state<string | null>(null);
	let preview = $state<RenamePreviewDto | null>(null);

	// Fill the form once per loaded user, without overwriting what is typed.
	$effect(() => {
		const user = me.data;
		if (user && loadedFor !== user.id) fill(user);
	});

	function fill(user: UserDto) {
		loadedFor = user.id ?? null;
		username = user.username ?? '';
		email = user.email ?? '';
		firstName = user.firstName ?? '';
		lastName = user.lastName ?? '';
	}

	const usernameInvalid = $derived(username.trim() !== '' && !isValidUsername(username.trim()));
	const emailInvalid = $derived(email.trim() !== '' && !isValidEmail(email.trim()));
	const dirty = $derived(
		!!me.data &&
			(username.trim() !== (me.data.username ?? '') ||
				email.trim() !== (me.data.email ?? '') ||
				firstName.trim() !== (me.data.firstName ?? '') ||
				lastName.trim() !== (me.data.lastName ?? ''))
	);
	const canSave = $derived(
		dirty && !saving && !usernameInvalid && !emailInvalid && !!username.trim() && !!email.trim()
	);

	const errorText: Record<string, () => string> = {
		invalid_username: m.settings_profile_error_username,
		rename_failed: m.settings_profile_error_rename,
		email_already_exists: m.settings_profile_error_email_taken,
		user_already_exists: m.settings_profile_error_username_taken
	};

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (!canSave || !me.data) return;
		serverError = null;
		const newName = username.trim();
		if (newName !== me.data.username) {
			// A rename moves the user's folder on disk: show what it touches first.
			saving = true;
			const { data, error } = await previewMyRename({ query: { newUsername: newName } });
			saving = false;
			if (!data || error) {
				serverError = m.settings_profile_error_rename();
				return;
			}
			if (!data.isValid) {
				serverError = data.errorMessage ?? m.settings_profile_error_username();
				return;
			}
			if (!data.isNoChange) {
				preview = data;
				return;
			}
		}
		await save();
	}

	async function save() {
		preview = null;
		saving = true;
		const { data, error, response } = await updateProfile({
			body: {
				username: username.trim(),
				email: email.trim(),
				firstName: firstName.trim() || null,
				lastName: lastName.trim() || null
			}
		});
		saving = false;
		if (data) {
			session.user = data;
			loadedFor = null;
			queryClient.setQueryData(getCurrentUserOptions().queryKey, data);
			toasts.show(m.settings_saved());
			return;
		}
		const code = apiErrorCode(error);
		serverError =
			(code && errorText[code]?.()) ||
			(response?.status === 403 ? m.settings_error_forbidden() : m.action_failed());
	}

	function formatDate(iso: string | null | undefined) {
		return iso
			? new Intl.DateTimeFormat(getLocale(), { dateStyle: 'long', timeStyle: 'short' }).format(
					new Date(iso)
				)
			: '—';
	}
</script>

<svelte:head>
	<title>{m.settings_profile()} · {m.app_name()}</title>
</svelte:head>

<div class="settings-page">
	<h1>{m.settings_profile()}</h1>
	<p class="lead">{m.settings_profile_lead()}</p>

	{#if me.isPending}
		<p class="hint" role="status">{m.session_restoring()}</p>
	{:else if !me.data}
		<p class="error" role="alert">{m.error_loading()}</p>
	{:else}
		<form class="card" onsubmit={submit} novalidate>
			<header>
				<h2>{m.settings_profile_details()}</h2>
			</header>
			<div class="fields">
				<label class="field">
					<span>{m.settings_profile_first_name()}</span>
					<input autocomplete="given-name" bind:value={firstName} />
				</label>
				<label class="field">
					<span>{m.settings_profile_last_name()}</span>
					<input autocomplete="family-name" bind:value={lastName} />
				</label>
				<label class="field">
					<span>{m.settings_profile_username()}</span>
					<input
						autocomplete="username"
						autocapitalize="none"
						spellcheck="false"
						required
						aria-invalid={usernameInvalid}
						aria-describedby="username-hint"
						bind:value={username}
					/>
					<span id="username-hint" class={usernameInvalid ? 'error' : 'hint'}>
						{usernameInvalid
							? m.settings_profile_error_username()
							: m.settings_profile_username_hint()}
					</span>
				</label>
				<label class="field">
					<span>{m.settings_profile_email()}</span>
					<input
						type="email"
						autocomplete="email"
						required
						aria-invalid={emailInvalid}
						aria-describedby={emailInvalid ? 'email-error' : undefined}
						bind:value={email}
					/>
					{#if emailInvalid}
						<span id="email-error" class="error">{m.settings_profile_error_email()}</span>
					{/if}
				</label>
			</div>
			{#if serverError}
				<p class="error" role="alert">{serverError}</p>
			{/if}
			<div class="actions">
				<button type="submit" class="button primary" disabled={!canSave}>
					{saving ? m.settings_saving() : m.settings_save()}
				</button>
				{#if dirty}
					<button type="button" class="button" onclick={() => me.data && fill(me.data)}>
						{m.settings_discard()}
					</button>
				{/if}
			</div>
		</form>

		<section class="card" aria-labelledby="account-facts">
			<header>
				<h2 id="account-facts">{m.settings_profile_account()}</h2>
			</header>
			<dl class="facts">
				<dt>{m.settings_profile_role()}</dt>
				<dd>
					{me.data.role === 'Admin' ? m.settings_role_admin() : m.settings_role_user()}
					{#if me.data.isPrimaryAdmin}· {m.settings_role_primary()}{/if}
				</dd>
				<dt>{m.settings_profile_created()}</dt>
				<dd>{formatDate(me.data.createdAt)}</dd>
				<dt>{m.settings_profile_last_login()}</dt>
				<dd>{formatDate(me.data.lastLoginAt)}</dd>
			</dl>
		</section>
	{/if}
</div>

<Dialog
	open={preview !== null}
	title={m.settings_rename_title()}
	onclose={() => (preview = null)}
	width="520px"
>
	{#if preview}
		<p>
			{m.settings_rename_body({ from: preview.currentUsername, to: preview.newUsername })}
		</p>
		<ul class="rename-impact">
			<li>{m.settings_rename_assets({ count: preview.assetsToUpdate })}</li>
			<li>{m.settings_rename_folders({ count: preview.foldersToUpdate })}</li>
			{#if preview.currentVirtualPath && preview.newVirtualPath}
				<li>
					<code>{preview.currentVirtualPath}</code> → <code>{preview.newVirtualPath}</code>
				</li>
			{/if}
		</ul>
		<p class="rename-note">{m.settings_rename_note()}</p>
	{/if}
	{#snippet actions()}
		<button type="button" onclick={() => (preview = null)}>{m.dialog_cancel()}</button>
		<button type="button" class="primary" onclick={save}>{m.settings_rename_confirm()}</button>
	{/snippet}
</Dialog>

<style>
	.rename-impact {
		margin: var(--space-3) 0;
		padding-left: var(--space-6);
		display: grid;
		gap: var(--space-1);
	}

	.rename-impact code {
		font-size: var(--font-size-sm);
		word-break: break-all;
	}

	.rename-note {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	p {
		margin: 0;
	}
</style>
