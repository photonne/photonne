<script lang="ts">
	import { renderSVG } from 'uqr';
	import { connectLink } from '#lib/account/connect-link.js';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';

	// The address this page was opened at: the one that reaches the server from
	// here, and usually from the phone too. If not, it can be typed in the app.
	const server = globalThis.location?.origin ?? '';
	const link = connectLink(server);
	const qr = renderSVG(link, { ecc: 'M', border: 2 });

	async function copy() {
		try {
			await navigator.clipboard.writeText(server);
			toasts.show(m.settings_mobile_copied());
		} catch {
			toasts.error(m.action_failed());
		}
	}
</script>

<svelte:head>
	<title>{m.settings_mobile()} · {m.app_name()}</title>
</svelte:head>

<div class="settings-page">
	<PageHeader title={m.settings_mobile()} subtitle={m.settings_mobile_lead()} />

	<section class="connect">
		<!-- The SVG is generated here from our own link: no user content in it. -->
		<figure class="qr" aria-label={m.settings_mobile_qr_label()}>
			<!-- eslint-disable-next-line svelte/no-at-html-tags -->
			{@html qr}
		</figure>
		<div class="steps">
			<ol>
				<li>{m.settings_mobile_step_install()}</li>
				<li>{m.settings_mobile_step_scan()}</li>
				<li>{m.settings_mobile_step_login()}</li>
			</ol>
			<p class="address">
				<span>{m.settings_mobile_address()}</span>
				<code>{server}</code>
				<button type="button" class="btn sm" onclick={copy}>{m.settings_mobile_copy()}</button>
			</p>
			<p class="hint">{m.settings_mobile_hint()}</p>
		</div>
	</section>
</div>

<style>
	.connect {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-6);
		align-items: flex-start;
	}

	.qr {
		width: 220px;
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
		min-width: 240px;
	}

	ol {
		display: grid;
		gap: var(--space-2);
		margin: 0 0 var(--space-4);
		padding-left: var(--space-5);
	}

	.address {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2);
		margin: 0;
	}

	.address span,
	.hint {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	code {
		overflow-wrap: anywhere;
	}

	.hint {
		margin: var(--space-3) 0 0;
	}
</style>
