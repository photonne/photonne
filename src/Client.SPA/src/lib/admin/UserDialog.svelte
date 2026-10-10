<script lang="ts">
	import {
		createUser,
		getSetting,
		previewUserRename,
		updateUser,
		type RenamePreviewDto,
		type UserDto
	} from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { errorText } from './errors.js';
	import PasswordRules from './PasswordRules.svelte';
	import { count } from './format.js';
	import { adminIcons } from './icons.js';
	import {
		canChangeRoleOrStatus,
		formToQuota,
		GIB,
		isValidEmail,
		isValidUsername,
		passwordChecks,
		QUOTA_PRESETS_GB,
		quotaToForm,
		type QuotaForm
	} from './users.js';

	interface Props {
		open: boolean;
		/** The user to edit; null creates a new one. */
		user: UserDto | null;
		me: UserDto | null;
		onclose: () => void;
		onsaved: (user: UserDto, created: boolean) => void;
	}

	let { open, user, me, onclose, onsaved }: Props = $props();

	let username = $state('');
	let email = $state('');
	let password = $state('');
	let showPassword = $state(false);
	let firstName = $state('');
	let lastName = $state('');
	let role = $state<'User' | 'Admin'>('User');
	let isActive = $state(true);
	let quota = $state<QuotaForm>({ choice: 'unlimited', customGb: 10 });

	let step = $state<'form' | 'rename'>('form');
	let preview = $state<RenamePreviewDto | null>(null);
	let saving = $state(false);
	let error = $state<string | null>(null);
	let touched = $state(false);

	const editing = $derived(user !== null);
	const protectedUser = $derived(user !== null && !canChangeRoleOrStatus(user, me));
	const checks = $derived(passwordChecks(password));

	const usernameError = $derived(
		touched && !isValidUsername(username.trim()) ? m.admin_user_username_invalid() : null
	);
	const emailError = $derived(
		touched && !isValidEmail(email) ? m.admin_user_email_invalid() : null
	);
	const passwordError = $derived(
		touched && !editing && !Object.values(checks).every(Boolean)
			? m.admin_user_password_weak()
			: null
	);

	// Fill the form each time the dialog opens.
	$effect(() => {
		if (open) reset();
	});

	async function reset() {
		step = 'form';
		preview = null;
		error = null;
		touched = false;
		saving = false;
		showPassword = false;
		password = '';
		username = user?.username ?? '';
		email = user?.email ?? '';
		firstName = user?.firstName ?? '';
		lastName = user?.lastName ?? '';
		role = user?.role === 'Admin' ? 'Admin' : 'User';
		isActive = user?.isActive ?? true;
		quota = quotaToForm(user?.storageQuotaBytes);
		if (!user) await loadDefaults();
	}

	/** New accounts start from the server's defaults (Settings › Users). */
	async function loadDefaults() {
		const read = async (key: string) => (await getSetting({ query: { key } })).data?.value ?? '';
		const [defaultRole, defaultActive, defaultQuota] = await Promise.all([
			read('UserSettings.DefaultRole'),
			read('UserSettings.DefaultIsActive'),
			read('UserSettings.DefaultStorageQuotaGb')
		]);
		if (user) return;
		role = defaultRole === 'Admin' ? 'Admin' : 'User';
		isActive = defaultActive.toLowerCase() !== 'false';
		const gb = Number.parseInt(defaultQuota, 10);
		quota = quotaToForm(Number.isFinite(gb) && gb > 0 ? gb * GIB : null);
	}

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		touched = true;
		error = null;
		if (usernameError || emailError || passwordError) return;

		if (user && username.trim() !== user.username) {
			saving = true;
			const { data, error: failure } = await previewUserRename({
				path: { id: user.id! },
				query: { newUsername: username.trim() }
			});
			saving = false;
			if (!data || !data.isValid) {
				error = data?.errorMessage ?? errorText(failure);
				return;
			}
			preview = data;
			step = 'rename';
			return;
		}
		await save();
	}

	async function save() {
		saving = true;
		error = null;
		const common = {
			username: username.trim(),
			email: email.trim(),
			firstName: firstName.trim() || null,
			lastName: lastName.trim() || null
		};
		const quotaBytes = formToQuota(quota);
		const result = user
			? await updateUser({
					path: { id: user.id! },
					body: {
						...common,
						// The server refuses these for the primary admin even unchanged.
						role: protectedUser ? null : role,
						isActive: protectedUser ? null : isActive,
						storageQuotaBytes: quotaBytes ?? -1
					}
				})
			: await createUser({
					body: { ...common, password, role, isActive, storageQuotaBytes: quotaBytes }
				});
		saving = false;
		if (result.data) {
			onsaved(result.data, !user);
		} else {
			step = 'form';
			error = errorText(result.error);
		}
	}

	const quotaChoices = $derived([
		{ value: 'unlimited', label: m.admin_user_quota_unlimited() },
		...QUOTA_PRESETS_GB.map((gb) => ({ value: `${gb}`, label: `${gb} GB` })),
		{ value: 'custom', label: m.admin_user_quota_custom() }
	]);
</script>

<Dialog
	{open}
	title={step === 'rename'
		? m.admin_user_rename_title()
		: editing
			? m.admin_user_edit_title()
			: m.admin_user_create_title()}
	onclose={() => !saving && onclose()}
	width="560px"
>
	{#if step === 'form'}
		<form id="user-form" class="form" novalidate onsubmit={submit}>
			<fieldset>
				<legend>{m.admin_user_section_account()}</legend>
				<label class="field">
					<span>{m.admin_user_username()}</span>
					<input
						bind:value={username}
						autocomplete="off"
						spellcheck="false"
						required
						maxlength="64"
						aria-invalid={usernameError ? 'true' : undefined}
						aria-describedby="username-hint"
					/>
					{#if usernameError}
						<p class="field-error" id="username-hint">{usernameError}</p>
					{:else}
						<p class="hint" id="username-hint">
							{editing ? m.admin_user_username_rename_hint() : m.admin_user_username_hint()}
						</p>
					{/if}
				</label>
				<label class="field">
					<span>{m.admin_user_email()}</span>
					<input
						type="email"
						bind:value={email}
						autocomplete="off"
						required
						aria-invalid={emailError ? 'true' : undefined}
					/>
					{#if emailError}<p class="field-error">{emailError}</p>{/if}
				</label>
				{#if !editing}
					<div class="field">
						<label for="user-password">{m.admin_user_password()}</label>
						<div class="password">
							<input
								id="user-password"
								type={showPassword ? 'text' : 'password'}
								bind:value={password}
								autocomplete="new-password"
								required
								aria-invalid={passwordError ? 'true' : undefined}
								aria-describedby="password-rules"
							/>
							<button
								type="button"
								class="icon-btn"
								aria-pressed={showPassword}
								aria-label={m.admin_core_show_password()}
								title={m.admin_core_show_password()}
								onclick={() => (showPassword = !showPassword)}
							>
								<Icon path={showPassword ? adminIcons.visibilityOff : adminIcons.visibility} />
							</button>
						</div>
						<PasswordRules {password} id="password-rules" />
						{#if passwordError}<p class="field-error">{passwordError}</p>{/if}
					</div>
				{/if}
			</fieldset>

			<fieldset>
				<legend>{m.admin_user_section_personal()}</legend>
				<div class="form-row">
					<label class="field">
						<span>{m.admin_user_first_name()}</span>
						<input bind:value={firstName} autocomplete="off" />
					</label>
					<label class="field">
						<span>{m.admin_user_last_name()}</span>
						<input bind:value={lastName} autocomplete="off" />
					</label>
				</div>
			</fieldset>

			<fieldset>
				<legend>{m.admin_user_section_access()}</legend>
				<div class="form-row">
					<label class="field">
						<span>{m.admin_user_role()}</span>
						<select bind:value={role} disabled={protectedUser}>
							<option value="User">{m.admin_core_role_user()}</option>
							<option value="Admin">{m.admin_core_role_admin()}</option>
						</select>
					</label>
					<label class="field">
						<span>{m.admin_user_quota()}</span>
						<select bind:value={quota.choice}>
							{#each quotaChoices as choice (choice.value)}
								<option value={choice.value}>{choice.label}</option>
							{/each}
						</select>
					</label>
					{#if quota.choice === 'custom'}
						<label class="field">
							<span>{m.admin_user_quota_custom_gb()}</span>
							<input type="number" min="1" step="1" bind:value={quota.customGb} />
						</label>
					{/if}
				</div>
				<label class="check">
					<input type="checkbox" bind:checked={isActive} disabled={protectedUser} />
					{m.admin_user_active()}
				</label>
				{#if protectedUser}
					<p class="hint">
						{user?.isPrimaryAdmin ? m.admin_user_primary_locked() : m.admin_user_self_locked()}
					</p>
				{/if}
			</fieldset>
		</form>
	{:else if preview}
		<div class="rename">
			<p class="notice warning">
				<Icon path={adminIcons.warning} />
				<span>{m.admin_user_rename_warning()}</span>
			</p>
			<p>
				<code>{preview.currentUsername}</code> → <code>{preview.newUsername}</code>
			</p>
			<dl>
				<dt>{m.admin_user_rename_folder()}</dt>
				<dd>
					<code>{preview.currentVirtualPath ?? '—'}</code> →
					<code>{preview.newVirtualPath ?? '—'}</code>
					{#if !preview.folderExistsOnDisk}
						<span class="chip tag warning">{m.admin_user_rename_folder_missing()}</span>
					{/if}
				</dd>
				<dt>{m.admin_user_rename_assets()}</dt>
				<dd class="num">{count(preview.assetsToUpdate)}</dd>
				<dt>{m.admin_user_rename_folders()}</dt>
				<dd class="num">{count(preview.foldersToUpdate)}</dd>
			</dl>
			{#if preview.assetsToUpdate > 1000 || preview.foldersToUpdate > 100}
				<p class="hint">{m.admin_user_rename_slow()}</p>
			{/if}
		</div>
	{/if}

	{#if error}<p class="field-error form-error" role="alert">{error}</p>{/if}

	{#snippet actions()}
		{#if step === 'rename'}
			<button type="button" onclick={() => (step = 'form')} disabled={saving}
				>{m.admin_core_back()}</button
			>
			<button type="button" class="primary" onclick={save} disabled={saving}
				>{m.admin_user_rename_confirm()}</button
			>
		{:else}
			<button type="button" onclick={onclose} disabled={saving}>{m.admin_core_cancel()}</button>
			<button type="submit" form="user-form" class="primary" disabled={saving}>
				{editing ? m.admin_core_save() : m.admin_user_create()}
			</button>
		{/if}
	{/snippet}
</Dialog>

<style>
	.password {
		display: flex;
		gap: var(--space-1);
		align-items: center;
	}

	.form-error {
		margin-top: var(--space-3);
	}

	.rename {
		display: grid;
		gap: var(--space-3);
	}

	.rename p {
		margin: 0;
	}

	dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--space-2) var(--space-4);
		margin: 0;
		font-size: var(--font-size-sm);
	}

	dt {
		color: var(--color-text-muted);
	}

	dd {
		margin: 0;
		overflow-wrap: anywhere;
	}

	code {
		padding: 0 4px;
		border-radius: 4px;
		background: var(--color-surface);
		font-size: 0.9em;
	}
</style>
