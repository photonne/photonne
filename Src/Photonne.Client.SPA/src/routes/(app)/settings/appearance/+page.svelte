<script lang="ts">
	import { getTheme, setTheme, type Theme } from '#lib/theme.js';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { getLocale, locales, setLocale, type Locale } from '#lib/paraglide/runtime.js';

	let theme = $state<Theme>(getTheme());

	const themes: { value: Theme; label: () => string; hint: () => string }[] = [
		{ value: 'system', label: m.settings_theme_system, hint: m.settings_theme_system_hint },
		{ value: 'light', label: m.settings_theme_light, hint: m.settings_theme_light_hint },
		{ value: 'dark', label: m.settings_theme_dark, hint: m.settings_theme_dark_hint }
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
		background: linear-gradient(135deg, #ffffff 50%, #f4f5f7 50%);
	}

	.swatch.dark {
		background: linear-gradient(135deg, #232428 50%, #111214 50%);
	}

	.swatch.system {
		background: linear-gradient(135deg, #ffffff 50%, #111214 50%);
	}
</style>
