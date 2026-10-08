<script lang="ts">
	import { createQuery, useQueryClient } from '@tanstack/svelte-query';
	import {
		createShareLink,
		revokeShareLink,
		updateShareLink,
		type ShareLinkResponse
	} from '#lib/api/index.js';
	import {
		listShareLinksOptions,
		listShareLinksQueryKey
	} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import LinkForm from './LinkForm.svelte';
	import {
		absoluteShareUrl,
		createLinkRequest,
		formFromLink,
		linkStatus,
		updateLinkRequest,
		type LinkForm as LinkFormModel
	} from './share-link.js';

	interface Props {
		albumId: string;
		onchanged: () => void;
	}

	let { albumId, onchanged }: Props = $props();

	const queryClient = useQueryClient();
	const links = createQuery(() => listShareLinksOptions({ query: { albumId } }));
	const key = $derived(listShareLinksQueryKey({ query: { albumId } }));

	/** 'new', a link's token while editing it, or null. */
	let editing = $state<string | null>(null);
	let form = $state<LinkFormModel>(formFromLink());
	let busy = $state(false);
	/** Token of the link waiting for a revoke confirmation. */
	let revoking = $state<string | null>(null);

	const dateFormat = new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium' });

	function urlOf(link: ShareLinkResponse) {
		return absoluteShareUrl(link.shareUrl, location.origin);
	}

	async function copy(link: ShareLinkResponse) {
		try {
			await navigator.clipboard.writeText(urlOf(link));
			toasts.show(m.albums_link_copied());
		} catch {
			toasts.error(m.albums_link_copy_failed());
		}
	}

	function edit(link: ShareLinkResponse | null) {
		form = formFromLink(link);
		editing = link?.token ?? 'new';
	}

	async function refresh() {
		await queryClient.invalidateQueries({ queryKey: key });
		onchanged();
	}

	async function save() {
		busy = true;
		const token = editing;
		const result =
			token === 'new'
				? await createShareLink({ body: createLinkRequest(albumId, form) })
				: await updateShareLink({ path: { token: token! }, body: updateLinkRequest(form) });
		busy = false;
		if (result.error || !result.data) {
			toasts.error(m.action_failed());
			return;
		}
		editing = null;
		await refresh();
		if (token === 'new') {
			const created = result.data as ShareLinkResponse;
			try {
				await navigator.clipboard.writeText(urlOf(created));
				toasts.show(m.albums_link_created_copied());
			} catch {
				toasts.show(m.albums_link_created());
			}
		} else toasts.show(m.albums_link_updated());
	}

	async function revoke() {
		const token = revoking;
		if (!token) return;
		busy = true;
		const { error } = await revokeShareLink({ path: { token } });
		busy = false;
		revoking = null;
		if (error) {
			toasts.error(m.action_failed());
			return;
		}
		await refresh();
		toasts.show(m.albums_link_revoked());
	}

	function summary(link: ShareLinkResponse) {
		const parts = [
			link.expiresAt
				? m.albums_link_until({ date: dateFormat.format(new Date(link.expiresAt)) })
				: m.albums_link_no_expiry(),
			link.maxViews !== null
				? m.albums_link_views_of({ count: link.viewCount, max: link.maxViews })
				: m.albums_link_views({ count: link.viewCount })
		];
		if (link.hasPassword) parts.push(m.albums_link_has_password());
		if (!link.allowDownload) parts.push(m.albums_link_no_download());
		if (link.allowUpload) parts.push(m.albums_link_uploads({ count: link.uploadCount }));
		return parts.join(' · ');
	}
</script>

<div class="panel">
	<p class="note">{m.albums_links_intro()}</p>

	{#if links.isPending}
		<p class="note" role="status">{m.session_restoring()}</p>
	{:else if links.isError}
		<p class="note" role="alert">{m.error_loading()}</p>
	{:else}
		<ul class="links" aria-label={m.albums_links_list()}>
			{#each links.data ?? [] as link (link.token)}
				{@const status = linkStatus(link)}
				<li class="link">
					{#if editing === link.token}
						<LinkForm
							bind:form
							hadPassword={link.hasPassword}
							submitLabel={m.albums_save()}
							{busy}
							onsubmit={save}
							oncancel={() => (editing = null)}
						/>
					{:else}
						<div class="url-row">
							<Icon name="link" size={18} />
							<input
								class="url"
								readonly
								value={urlOf(link)}
								aria-label={m.albums_link_url()}
								onfocus={(event) => event.currentTarget.select()}
							/>
							{#if status !== 'active'}
								<span class="status">
									{status === 'expired' ? m.albums_link_expired() : m.albums_link_used_up()}
								</span>
							{/if}
						</div>
						<p class="summary">{summary(link)}</p>
						{#if revoking === link.token}
							<!-- Confirmed in place: this already lives in a dialog. -->
							<div class="buttons confirm" role="group" aria-label={m.albums_link_revoke_title()}>
								<span class="question">
									<strong>{m.albums_link_revoke_title()}</strong>
									{m.albums_link_revoke_message()}
								</span>
								<!-- svelte-ignore a11y_autofocus -->
								<button type="button" autofocus onclick={() => (revoking = null)}
									>{m.dialog_cancel()}</button
								>
								<button type="button" class="danger solid" disabled={busy} onclick={revoke}>
									{m.albums_link_revoke()}
								</button>
							</div>
						{:else}
							<div class="buttons">
								<button type="button" onclick={() => copy(link)}>
									<Icon name="copy" size={16} />{m.albums_link_copy()}
								</button>
								<button type="button" onclick={() => edit(link)}>
									<Icon name="edit" size={16} />{m.albums_link_edit()}
								</button>
								<button type="button" class="danger" onclick={() => (revoking = link.token)}>
									<Icon name="delete" size={16} />{m.albums_link_revoke()}
								</button>
							</div>
						{/if}
					{/if}
				</li>
			{:else}
				<li class="note">{m.albums_links_none()}</li>
			{/each}
		</ul>

		{#if editing === 'new'}
			<LinkForm
				bind:form
				hadPassword={false}
				submitLabel={m.albums_link_create()}
				{busy}
				onsubmit={save}
				oncancel={() => (editing = null)}
			/>
		{:else}
			<button type="button" class="new" onclick={() => edit(null)}>
				<Icon name="add" size={18} />{m.albums_link_new()}
			</button>
		{/if}
	{/if}
</div>

<style>
	.panel {
		display: grid;
		gap: var(--space-3);
	}

	.note {
		margin: 0;
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.links {
		display: grid;
		gap: var(--space-2);
		margin: 0;
		padding: 0;
		list-style: none;
	}

	.link:not(:has(form)) {
		display: grid;
		gap: var(--space-1);
		padding: var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
	}

	.url-row {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.url {
		flex: 1;
		min-width: 0;
		padding: var(--space-1) var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-family: ui-monospace, monospace;
		font-size: var(--font-size-sm);
	}

	.status {
		padding: 2px var(--space-2);
		border-radius: 999px;
		background: color-mix(in srgb, var(--color-danger) 18%, transparent);
		color: var(--color-danger);
		font-size: var(--font-size-xs);
		font-weight: 600;
		white-space: nowrap;
	}

	.summary {
		margin: 0;
		font-size: var(--font-size-sm);
		color: var(--color-text-muted);
	}

	.buttons {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-2);
	}

	.buttons button,
	.new {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		padding: var(--space-1) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: transparent;
		font-size: var(--font-size-sm);
		cursor: pointer;
	}

	.buttons button:hover,
	.new:hover {
		background: var(--color-surface);
	}

	.buttons .danger {
		color: var(--color-danger);
	}

	.buttons .danger.solid {
		border-color: var(--color-danger);
		background: var(--color-danger);
		color: #fff;
		font-weight: 600;
	}

	.confirm {
		align-items: center;
		padding: var(--space-2);
		border-radius: var(--radius-sm);
		background: color-mix(in srgb, var(--color-danger) 10%, transparent);
	}

	.question {
		flex: 1 1 260px;
		font-size: var(--font-size-sm);
	}

	.new {
		justify-self: start;
	}
</style>
