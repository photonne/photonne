import type { QueryClient } from '@tanstack/svelte-query';
import {
	moveFolderAssets,
	setOrganizeExcluded,
	type MoveFolderAssetsResponse
} from '#lib/api/index.js';
import {
	getAllFoldersQueryKey,
	getFolderTreeQueryKey,
	getOrganizeInboxCountQueryKey,
	getOrganizeSuggestionsQueryKey
} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { m } from '#lib/paraglide/messages.js';
import { yearSummary } from './review.js';

/** Files `ids` out of the inbox into `folder` (optionally into year subfolders). */
export async function moveToFolder(
	ids: readonly string[],
	folder: { id: string },
	organizeByCaptureYear: boolean
) {
	const { data, error } = await moveFolderAssets({
		body: {
			sourceFolderId: null,
			targetFolderId: folder.id,
			assetIds: [...ids],
			organizeByCaptureYear
		}
	});
	if (error || !data) throw error ?? new Error('move failed');
	return data;
}

/** Sets photos aside from the inbox (or puts them back). */
export async function setExcluded(ids: readonly string[], excluded: boolean) {
	const { error } = await setOrganizeExcluded({ body: { assetIds: [...ids], excluded } });
	if (error) throw error;
}

/** "12 movidas a «Viajes» (2023: 5 · 2024: 7)". */
export function movedMessage(result: MoveFolderAssetsResponse, folder: string) {
	const message = m.organize_moved({ count: result.moved, folder });
	return result.yearBreakdown.length
		? `${message} (${yearSummary(result.yearBreakdown)})`
		: message;
}

/** After a move or a set-aside: counts, suggestions and folder sizes change. */
export function refreshOrganize(queryClient: QueryClient) {
	for (const queryKey of [
		getOrganizeInboxCountQueryKey(),
		getOrganizeSuggestionsQueryKey(),
		getFolderTreeQueryKey(),
		getAllFoldersQueryKey()
	]) {
		queryClient.invalidateQueries({ queryKey });
	}
}
