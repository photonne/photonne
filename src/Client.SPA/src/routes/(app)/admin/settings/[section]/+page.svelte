<script lang="ts">
	import SettingsEditor from '#lib/adminops/SettingsEditor.svelte';
	import { findSection } from '#lib/adminops/settings-sections.js';
	import { m } from '#lib/paraglide/messages.js';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const section = $derived(findSection(data.sectionId)!);
</script>

<svelte:head>
	<title>{section.title()} · {m.admin_settings()} · {m.app_name()}</title>
</svelte:head>

<!-- A fresh editor per section: its form holds that section's keys only. -->
{#key section.id}
	<SettingsEditor {section} />
{/key}
