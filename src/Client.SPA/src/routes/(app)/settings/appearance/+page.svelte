<script lang="ts">
	import { getTheme, setTheme, type Theme } from '#lib/theme.js';
	import { gridLayout } from '#lib/timeline/grid-layout-preference.svelte.js';
	import type { GridLayoutMode } from '#lib/timeline/grid-model.js';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale, locales, setLocale, type Locale } from '#lib/paraglide/runtime.js';

	let theme = $state<Theme>(getTheme());

	const themes: { value: Theme; label: () => string; hint: () => string }[] = [
		{ value: 'system', label: m.settings_theme_system, hint: m.settings_theme_system_hint },
		{ value: 'light', label: m.settings_theme_light, hint: m.settings_theme_light_hint },
		{ value: 'dark', label: m.settings_theme_dark, hint: m.settings_theme_dark_hint }
	];

	const layouts: { value: GridLayoutMode; label: () => string; hint: () => string }[] = [
		{
			value: 'masonry',
			label: m.settings_grid_layout_masonry,
			hint: m.settings_grid_layout_masonry_hint
		},
		{ value: 'rows', label: m.settings_grid_layout_rows, hint: m.settings_grid_layout_rows_hint }
	];

	// Each language in its own name, so it can be found whatever is showing.
	const localeNames: Record<Locale, string> = { es: 'Español', en: 'English' };

	function chooseTheme(value: Theme) {
		theme = value;
		setTheme(value);
	}
</script>

<svelte:head>
	<title>{m.settings_appearance()} · {m.app_name()}</title>
</svelte:head>

<div class="settings-page">
	<PageHeader title={m.settings_appearance()} subtitle={m.settings_appearance_lead()} />

	<div class="settings-body">
		<section class="card" aria-labelledby="theme-title">
			<header>
				<h2 id="theme-title">{m.settings_theme()}</h2>
				<p>{m.settings_theme_lead()}</p>
			</header>
			<fieldset class="choices">
				<legend class="visually-hidden">{m.settings_theme()}</legend>
				{#each themes as option (option.value)}
					<label class="choice theme-choice">
						<input
							type="radio"
							name="theme"
							value={option.value}
							checked={theme === option.value}
							onchange={() => chooseTheme(option.value)}
						/>
						<span class="swatch {option.value}" aria-hidden="true"></span>
						<span class="text">
							<span>{option.label()}</span>
							<small>{option.hint()}</small>
						</span>
					</label>
				{/each}
			</fieldset>
		</section>

		<section class="card" aria-labelledby="layout-title">
			<header>
				<h2 id="layout-title">{m.settings_grid_layout()}</h2>
				<p>{m.settings_grid_layout_lead()}</p>
			</header>
			<fieldset class="choices">
				<legend class="visually-hidden">{m.settings_grid_layout()}</legend>
				{#each layouts as option (option.value)}
					<label class="choice theme-choice">
						<input
							type="radio"
							name="grid-layout"
							value={option.value}
							checked={gridLayout.value === option.value}
							onchange={() => gridLayout.set(option.value)}
						/>
						<span class="swatch layout {option.value}" aria-hidden="true"></span>
						<span class="text">
							<span>{option.label()}</span>
							<small>{option.hint()}</small>
						</span>
					</label>
				{/each}
			</fieldset>
		</section>

		<section class="card" aria-labelledby="language-title">
			<header>
				<h2 id="language-title">{m.language()}</h2>
				<p>{m.settings_language_lead()}</p>
			</header>
			<fieldset class="choices">
				<legend class="visually-hidden">{m.language()}</legend>
				{#each locales as locale (locale)}
					<label class="choice" lang={locale}>
						<input
							type="radio"
							name="language"
							value={locale}
							checked={getLocale() === locale}
							onchange={() => setLocale(locale)}
						/>
						{localeNames[locale]}
					</label>
				{/each}
			</fieldset>
		</section>
	</div>
</div>

<style>
	.theme-choice {
		align-items: flex-start;
	}

	.theme-choice input {
		margin-top: 4px;
	}

	.text {
		display: grid;
	}

	.text small {
		color: var(--color-text-muted);
		font-weight: 400;
	}

	.swatch {
		flex: none;
		width: 36px;
		height: 28px;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
	}

	.swatch.light {
		background: linear-gradient(135deg, #ffffff 50%, #f8f9fa 50%);
	}

	.swatch.dark {
		background: linear-gradient(135deg, #1c1c20 50%, #0a0a0c 50%);
	}

	.swatch.system {
		background: linear-gradient(135deg, #f8f9fa 50%, #0a0a0c 50%);
	}

	/* A sketch of each layout: three columns of blocks, or two rows. */
	.swatch.layout {
		background-color: var(--color-surface);
		background-repeat: no-repeat;
	}

	.swatch.layout.masonry {
		background-image:
			linear-gradient(var(--color-text-muted), var(--color-text-muted)),
			linear-gradient(var(--color-text-muted), var(--color-text-muted)),
			linear-gradient(var(--color-text-muted), var(--color-text-muted)),
			linear-gradient(var(--color-text-muted), var(--color-text-muted)),
			linear-gradient(var(--color-text-muted), var(--color-text-muted));
		background-size:
			8px 10px,
			8px 8px,
			8px 16px,
			8px 6px,
			8px 12px;
		background-position:
			4px 4px,
			4px 16px,
			14px 4px,
			24px 4px,
			24px 12px;
	}

	.swatch.layout.rows {
		background-image:
			linear-gradient(var(--color-text-muted), var(--color-text-muted)),
			linear-gradient(var(--color-text-muted), var(--color-text-muted)),
			linear-gradient(var(--color-text-muted), var(--color-text-muted)),
			linear-gradient(var(--color-text-muted), var(--color-text-muted));
		background-size:
			12px 8px,
			14px 8px,
			8px 8px,
			18px 8px;
		background-position:
			4px 4px,
			18px 4px,
			4px 16px,
			14px 16px;
	}
</style>
