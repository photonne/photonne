<script lang="ts">
	import { resetPassword, type UserDto } from '#lib/api/index.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { errorText } from './errors.js';
	import PasswordRules from './PasswordRules.svelte';
	import { isStrongPassword } from './users.js';

	interface Props {
		user: UserDto | null;
		onclose: () => void;
		ondone: (user: UserDto) => void;
	}

	let { user, onclose, ondone }: Props = $props();

	let password = $state('');
	let confirm = $state('');
	let saving = $state(false);
	let error = $state<string | null>(null);

	$effect(() => {
		if (user) {
			password = '';
			confirm = '';
			error = null;
		}
	});

	const mismatch = $derived(confirm.length > 0 && confirm !== password);
	const valid = $derived(isStrongPassword(password) && confirm === password);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		if (!user || !valid) return;
		saving = true;
		const { data, error: failure } = await resetPassword({
			path: { id: user.id! },
			body: { newPassword: password }
		});
		saving = false;
		if (data) ondone(user);
		else error = errorText(failure);
	}
</script>

<Dialog
	open={user !== null}
	title={m.admin_reset_title()}
	onclose={() => !saving && onclose()}
	width="460px"
>
	<form id="reset-form" class="form" onsubmit={submit}>
		<p class="intro">{m.admin_reset_intro({ name: user?.username ?? '' })}</p>
		<div class="field">
			<label for="reset-password">{m.admin_reset_new()}</label>
			<input
				id="reset-password"
				type="password"
				bind:value={password}
				autocomplete="new-password"
				required
				aria-describedby="reset-rules"
			/>
			<PasswordRules {password} id="reset-rules" />
		</div>
		<label class="field">
			<span>{m.admin_reset_confirm()}</span>
			<input
				type="password"
				bind:value={confirm}
				autocomplete="new-password"
				required
				aria-invalid={mismatch ? 'true' : undefined}
			/>
			{#if mismatch}<p class="field-error">{m.admin_reset_mismatch()}</p>{/if}
		</label>
		{#if error}<p class="field-error" role="alert">{error}</p>{/if}
	</form>
	{#snippet actions()}
		<button type="button" onclick={onclose} disabled={saving}>{m.admin_core_cancel()}</button>
		<button type="submit" form="reset-form" class="primary" disabled={!valid || saving}>
			{m.admin_reset_submit()}
		</button>
	{/snippet}
</Dialog>

<style>
	.intro {
		margin: 0;
	}
</style>
