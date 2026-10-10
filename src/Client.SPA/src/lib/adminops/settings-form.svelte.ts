import type { QueryClient } from '@tanstack/svelte-query';
import { saveSetting } from '#lib/api/index.js';
import {
	getSettingOptions,
	getSettingQueryKey
} from '#lib/api/generated/@tanstack/svelte-query.gen.js';
import { isVisible, sectionFields, type SettingsSection } from './settings-sections.js';
import { changes, validate, withDefaults, type Values } from './settings-model.js';

export type SaveOutcome = { saved: number; failed: string[] };

/**
 * The editable copy of one settings section: loads every key, tracks what
 * changed, validates as the admin types, and saves only what differs.
 */
export class SettingsForm {
	readonly section: SettingsSection;
	readonly fields;
	original = $state<Values>({});
	values = $state<Values>({});
	status = $state<'loading' | 'ready' | 'error'>('loading');
	saving = $state(false);

	visibleFields = $derived.by(() => this.fields.filter((field) => isVisible(field, this.values)));
	errors = $derived.by(() =>
		this.status === 'ready'
			? validate(this.visibleFields, this.values, this.section.crossCheck)
			: {}
	);
	pending = $derived.by(() => changes(this.fields, this.original, this.values));
	dirty = $derived(this.pending.length > 0);
	valid = $derived(Object.keys(this.errors).length === 0);
	canSave = $derived(this.status === 'ready' && this.dirty && this.valid && !this.saving);

	#queryClient: QueryClient;

	constructor(section: SettingsSection, queryClient: QueryClient) {
		this.section = section;
		this.fields = sectionFields(section);
		this.#queryClient = queryClient;
	}

	async load() {
		this.status = 'loading';
		try {
			const fetched = await Promise.all(
				this.fields.map((field) =>
					this.#queryClient.fetchQuery({
						...getSettingOptions({ query: { key: field.key } }),
						staleTime: 0
					})
				)
			);
			const stored: Values = {};
			for (const { key, value } of fetched) stored[key] = value;
			this.original = withDefaults(this.fields, stored);
			this.values = { ...this.original };
			this.status = 'ready';
		} catch {
			// Nothing trustworthy to edit: a page of defaults would pass for
			// the server's values.
			this.status = 'error';
		}
	}

	set(key: string, value: string) {
		this.values[key] = value;
	}

	reset() {
		this.values = { ...this.original };
	}

	async save(): Promise<SaveOutcome> {
		const pending = this.pending;
		if (!this.canSave) return { saved: 0, failed: [] };
		this.saving = true;
		const results = await Promise.allSettled(
			pending.map(([key, value]) => saveSetting({ body: { key, value }, throwOnError: true }))
		);
		const failed: string[] = [];
		const saved: Values = {};
		results.forEach((result, index) => {
			const [key, value] = pending[index];
			if (result.status === 'fulfilled') {
				saved[key] = value;
				this.#queryClient.setQueryData(getSettingQueryKey({ query: { key } }), { key, value });
			} else failed.push(key);
		});
		// Only what reached the server becomes the new baseline: the fields stay
		// live during the request, and an edit made meanwhile isn't saved yet.
		this.original = { ...this.original, ...saved };
		this.saving = false;
		return { saved: Object.keys(saved).length, failed };
	}
}
