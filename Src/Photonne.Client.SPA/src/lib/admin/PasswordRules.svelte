<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { adminIcons } from './icons.js';
	import { passwordChecks, type PasswordChecks } from './users.js';

	/** The server's password rules as a live checklist under the field. */
	let { password, id }: { password: string; id: string } = $props();

	const rules: [keyof PasswordChecks, () => string][] = [
		['length', m.admin_core_password_rule_length],
		['upper', m.admin_core_password_rule_upper],
		['lower', m.admin_core_password_rule_lower],
		['digit', m.admin_core_password_rule_digit],
		['symbol', m.admin_core_password_rule_symbol]
	];

	const checks = $derived(passwordChecks(password));
</script>

<ul class="rules" {id}>
	{#each rules as [key, label] (key)}
		<li class:ok={checks[key]}>
			<Icon path={checks[key] ? adminIcons.checkCircle : adminIcons.error} size={14} />
			<span>{label()}</span>
			<span class="visually-hidden"
				>{checks[key] ? m.admin_core_rule_met() : m.admin_core_rule_unmet()}</span
			>
		</li>
	{/each}
</ul>

<style>
	.rules {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-1) var(--space-3);
		margin: 0;
		padding: 0;
		list-style: none;
		font-size: var(--font-size-xs);
		color: var(--color-text-muted);
	}

	li {
		display: inline-flex;
		align-items: center;
		gap: 4px;
	}

	li.ok {
		color: var(--color-success);
	}
</style>
