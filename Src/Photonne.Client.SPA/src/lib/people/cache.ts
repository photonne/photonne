import type { QueryClient } from '@tanstack/svelte-query';

// Generated query ids of everything that shows people or their faces. A
// partial key ({ _id }) matches every query of that operation, whatever its
// parameters, infinite or not.
const PEOPLE_QUERIES = [
	'getApiPeople',
	'getApiPeopleById',
	'getApiPeopleByIdFaces',
	'getApiPeopleByIdSuggestions',
	'getApiAssetsByIdFaces'
];

/** After any change to people or faces: names, counts, covers and lists refetch. */
export function invalidatePeople(queryClient: QueryClient) {
	return Promise.all(
		PEOPLE_QUERIES.map((_id) => queryClient.invalidateQueries({ queryKey: [{ _id }] }))
	);
}
