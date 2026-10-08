<script lang="ts">
	import { untrack } from 'svelte';
	import { useQueryClient } from '@tanstack/svelte-query';
	import { invalidateAlbums } from '#lib/albums/cache.js';
	import LinkForm from '#lib/albums/LinkForm.svelte';
	import {
		absoluteShareUrl,
		formFromLink,
		type LinkForm as LinkFormModel
	} from '#lib/albums/share-link.js';
	import {
		addAssetsToAlbumBatch,
		createAlbum,
		createShareLink,
		deleteAlbum,
		type ShareLinkResponse
	} from '#lib/api/index.js';
	import { getSentShareLinksQueryKey } from '#lib/api/generated/@tanstack/svelte-query.gen.js';
	import Dialog from '#lib/components/Dialog.svelte';
	import Icon from '#lib/components/Icon.svelte';
	import { toasts } from '#lib/components/toasts.svelte.js';
	import { appHref } from '#lib/navigation/href.js';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale } from '#lib/paraglide/runtime.js';
	import { icons } from './icons.js';
	import {
		SHARE_NAME_MAX,
		shareAssetsAsLink,
		validateShareName,
		type ShareAssetsApi
	} from './share-assets.js';

	interface Props {
		/** The photos to share (one, from the viewer, or a selection); null keeps it closed. */
		assetIds: readonly string[] | null;
		onclose: () => void;
		/** The album and link exist (e.g. to clear the selection that made them). */
		oncreated?: () => void;
	}

	let { assetIds, onclose, oncreated }: Props = $props();

	const queryClient = useQueryClient();
	const nameId = $props.id();

	let name = $state('');
	let form = $state<LinkFormModel>(formFromLink());
	let tried = $state(false);
	let busy = $state(false);
	let created = $state<{ albumId: string; url: string } | null>(null);
	let nameInput = $state<HTMLInputElement>();

	const count = $derived(assetIds?.length ?? 0);
	const nameError = $derived(validateShareName(name));

	// Each opening starts over, with a name that says what and when.
	$effect(() => {
		if (!assetIds) return;
		const total = assetIds.length;
		untrack(() => {
			const date = new Intl.DateTimeFormat(getLocale(), { dateStyle: 'medium' }).format(Date.now());
			name = m.links_share_default_name({ count: total, date });
			form = formFromLink();
			tried = false;
			created = null;
		});
	});

	const api: ShareAssetsApi = {
		async createAlbum(albumName) {
			const { data, error } = await createAlbum({ body: { name: albumName } });
			if (error || !data) throw error ?? new Error('No album');
			return data;
		},
		async addAssets(albumId, ids) {
			const { error } = await addAssetsToAlbumBatch({
				path: { albumId },
				body: { assetIds: ids }
			});
			if (error) throw error;
		},
		async createLink(body) {
			const { data, error } = await createShareLink({ body });
			if (error || !data) throw error ?? new Error('No link');
			return data as ShareLinkResponse;
		},
		async deleteAlbum(albumId) {
			await deleteAlbum({ path: { albumId } });
		}
	};

	async function submit() {
		tried = true;
		if (nameError || !assetIds || busy) {
			nameInput?.focus();
			return;
		}
		busy = true;
		try {
			const result = await shareAssetsAsLink(api, assetIds, name, form);
			created = {
				albumId: result.albumId,
				url: absoluteShareUrl(result.link.shareUrl, location.origin)
			};
			invalidateAlbums(queryClient);
			queryClient.invalidateQueries({ queryKey: getSentShareLinksQueryKey() });
			oncreated?.();
		} catch {
			toasts.error(m.links_share_failed());
		} finally {
			busy = false;
		}
	}

	async function copy() {
		if (!created) return;
		try {
			await navigator.clipboard.writeText(created.url);
			toasts.show(m.albums_link_copied());
		} catch {
			toasts.error(m.albums_link_copy_failed());
		}
	}

	function close() {
		if (!busy) onclose();
	}

	// The page behind (the viewer's arrows and Escape, a selection's Delete)
	// must not act while this is up; the dialog's own keys still work.
	function onkeydowncapture(event: KeyboardEvent) {
		if (assetIds) event.stopPropagation();
	}
</script>

<svelte:window {onkeydowncapture} />

<Dialog
	open={assetIds !== null}
	title={created ? m.links_share_ready() : m.links_share_title({ count })}
	onclose={close}
	width="520px"
>
	{#if created}
		<div class="result">
			<p class="note">{m.links_share_ready_hint()}</p>
			<div class="url-row">
				<Icon name="link" size={18} />
				<!-- svelte-ignore a11y_autofocus -->
				<input
					class="url"
					readonly
					autofocus
					value={created.url}
					aria-label={m.albums_link_url()}
					onfocus={(event) => event.currentTarget.select()}
				/>
			</div>
			<div class="links">
				<a href={created.url} target="_blank" rel="noopener noreferrer">
					<Icon path={icons.openInNew} size={16} />{m.links_open()}
				</a>
				<a href={appHref(`/albums/${created.albumId}`)} onclick={onclose}>
					<Icon name="album" size={16} />{m.links_share_view_album()}
				</a>
			</div>
		</div>
	{:else}
		<p class="note">{m.links_share_intro({ count })}</p>
		<LinkForm
			bind:form
			hadPassword={false}
			submitLabel={m.albums_link_create()}
			{busy}
			onsubmit={submit}
			oncancel={close}
		>
			{#snippet leading()}
				<div class="name">
					<label for={nameId}>{m.links_share_name()}</label>
					<!-- svelte-ignore a11y_autofocus -->
					<input
						id={nameId}
						bind:this={nameInput}
						bind:value={name}
						autofocus
						maxlength={SHARE_NAME_MAX}
						aria-invalid={tried && nameError !== null}
						aria-describedby="{nameId}-hint"
						onfocus={(event) => event.currentTarget.select()}
					/>
					<span id="{nameId}-hint" class="hint" class:error={tried && nameError}>
						{tried && nameError ? m.links_share_name_required() : m.links_share_name_hint()}
					</span>
				</div>
			{/snippet}
		</LinkForm>
	{/if}

	{#snippet actions()}
		{#if created}
			<button type="button" onclick={onclose}>{m.links_close()}</button>
			<button type="button" class="primary" onclick={copy}>
				{m.albums_link_copy()}
			</button>
		{/if}
	{/snippet}
</Dialog>

<style>
	.note {
		margin: 0 0 var(--space-3);
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.name {
		display: grid;
		gap: var(--space-1);
	}

	.name label {
		font-size: var(--font-size-sm);
		font-weight: 600;
	}

	.name input {
		padding: var(--space-2) var(--space-3);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-bg);
	}

	.name input[aria-invalid='true'] {
		border-color: var(--color-danger);
	}

	.hint {
		color: var(--color-text-muted);
		font-size: var(--font-size-xs);
	}

	.hint.error {
		color: var(--color-danger);
	}

	.result {
		display: grid;
		gap: var(--space-3);
	}

	.result .note {
		margin: 0;
	}

	.url-row {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}

	.url {
		flex: 1;
		min-width: 0;
		padding: var(--space-2);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		background: var(--color-surface);
		font-family: ui-monospace, monospace;
		font-size: var(--font-size-sm);
	}

	.links {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-4);
	}

	.links a {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		color: var(--color-accent);
		font-size: var(--font-size-sm);
	}
</style>
