import { error } from '@sveltejs/kit';
import { findSection } from '#lib/adminops/settings-sections.js';
import type { PageLoad } from './$types';

export const load: PageLoad = ({ params }) => {
	const section = findSection(params.section);
	if (!section) error(404);
	return { sectionId: section.id };
};
