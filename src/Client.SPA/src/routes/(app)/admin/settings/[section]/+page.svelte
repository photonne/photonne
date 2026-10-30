<script lang="ts">
	import SettingsEditor from '#lib/adminops/SettingsEditor.svelte';
	import SettingsLayout from '#lib/adminops/SettingsLayout.svelte';
	import { findSection, type SettingsSection } from '#lib/adminops/settings-sections.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	// When leaving for another page, this one still renders once with the next
	// page's data, which has no section: it keeps the last one until it goes.
	let last: SettingsSection | undefined;
	const section = $derived.by(() => {
		last = findSection(data.sectionId) ?? last;
		return last!;
	});
</script>

<svelte:head>
	<title>{section.title()} · {m.admin_settings()} · {m.app_name()}</title>
</svelte:head>

<SettingsLayout current={section.id}>
	<!-- A fresh editor per section: its form holds that section's keys only. -->
	{#key section.id}
		<SettingsEditor {section} />
	{/key}
</SettingsLayout>
