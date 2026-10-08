<script lang="ts" module>
	let zones: string[] | null = null;

	/** Every IANA zone the browser knows, UTC first. */
	function timeZones() {
		if (!zones) {
			let all: string[];
			try {
				all = Intl.supportedValuesOf('timeZone');
			} catch {
				all = [];
			}
			zones = ['UTC', ...all.filter((zone) => zone !== 'UTC')];
		}
		return zones;
	}
</script>

<script lang="ts">
	import Icon from '#lib/components/Icon.svelte';
	import { m } from '#lib/paraglide/messages.js';
	import { icons } from './icons.js';
	import { isOn, parseNumber, type FieldDef, type FieldError } from './settings-model.js';

	interface Props {
		field: FieldDef;
		value: string;
		error: FieldError | undefined;
		/** Differs from what the server has. */
		changed: boolean;
		disabled: boolean;
		onchange: (value: string) => void;
	}

	let { field, value, error, changed, disabled, onchange }: Props = $props();

	const id = $derived(`setting-${field.key.replaceAll('.', '-')}`);
	const hintId = $derived(`${id}-hint`);
	const errorId = $derived(`${id}-error`);
	const describedBy = $derived(
		[field.hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined
	);

	let reveal = $state(false);

	const errorText = $derived.by(() => {
		switch (error) {
			case 'required':
				return m.ops_field_required();
			case 'notNumber':
				return field.kind === 'decimal' ? m.ops_field_not_decimal() : m.ops_field_not_integer();
			case 'range':
				return m.ops_field_range({ min: String(field.min), max: String(field.max) });
			case 'time':
				return m.ops_field_time();
			case 'url':
				return m.ops_field_url();
			case 'cross':
				return m.ops_field_suggestion_band();
			default:
				return '';
		}
	});

	const zoneOptions = $derived.by(() => {
		if (field.kind !== 'timezone') return [];
		const list = timeZones();
		return value && !list.includes(value) ? [value, ...list] : list;
	});

	/** A stored value outside the options is still shown, not silently replaced. */
	const selectOptions = $derived.by(() => {
		const options = field.options ?? [];
		return value && !options.some((option) => option.value === value)
			? [...options, { value, label: () => value }]
			: options;
	});

	const sliderValue = $derived.by(() => {
		const number = parseNumber(field, value);
		if (number === null) return field.min ?? 0;
		return Math.min(field.max ?? number, Math.max(field.min ?? number, number));
	});

	const warning = $derived(
		field.warnWhen && value === field.warnWhen.value ? field.warnWhen.message() : null
	);
</script>

{#if field.kind === 'bool'}
	<div class="field switch-field">
		<div class="text">
			<label for={id}
				>{field.label()}{#if changed}<span class="dot" aria-hidden="true"></span>{/if}</label
			>
			{#if field.hint}<p class="hint" id={hintId}>{field.hint()}</p>{/if}
		</div>
		<input
			{id}
			class="switch"
			type="checkbox"
			role="switch"
			checked={isOn(value)}
			{disabled}
			aria-describedby={describedBy}
			onchange={(event) => onchange(event.currentTarget.checked ? 'true' : 'false')}
		/>
	</div>
{:else}
	<div class="field">
		<label for={id}>
			{field.label()}{#if changed}<span class="dot" aria-hidden="true"></span>{/if}
		</label>
		<div class="control">
			{#if field.kind === 'select'}
				<select
					{id}
					class="ops-select"
					{value}
					{disabled}
					aria-describedby={describedBy}
					aria-invalid={error ? 'true' : undefined}
					onchange={(event) => onchange(event.currentTarget.value)}
				>
					{#each selectOptions as option (option.value)}
						<option value={option.value}>{option.label()}</option>
					{/each}
				</select>
			{:else if field.kind === 'timezone'}
				<select
					{id}
					class="ops-select"
					{value}
					{disabled}
					aria-describedby={describedBy}
					onchange={(event) => onchange(event.currentTarget.value)}
				>
					{#each zoneOptions as zone (zone)}
						<option value={zone}>{zone.replaceAll('_', ' ')}</option>
					{/each}
				</select>
			{:else if field.kind === 'int' || field.kind === 'decimal'}
				{#if field.slider}
					<input
						class="range"
						type="range"
						min={field.min}
						max={field.max}
						step={field.step}
						value={sliderValue}
						{disabled}
						aria-label={field.label()}
						aria-describedby={describedBy}
						oninput={(event) =>
							onchange(
								field.kind === 'decimal'
									? Number(event.currentTarget.value).toFixed(2)
									: event.currentTarget.value
							)}
					/>
				{/if}
				<input
					{id}
					class="ops-input number"
					class:short={field.slider}
					type="text"
					inputmode={field.kind === 'decimal' ? 'decimal' : 'numeric'}
					{value}
					{disabled}
					aria-describedby={describedBy}
					aria-invalid={error ? 'true' : undefined}
					oninput={(event) => onchange(event.currentTarget.value)}
				/>
				{#if field.unit}<span class="unit">{field.unit()}</span>{/if}
			{:else if field.kind === 'time'}
				<input
					{id}
					class="ops-input"
					type="time"
					{value}
					{disabled}
					aria-describedby={describedBy}
					aria-invalid={error ? 'true' : undefined}
					oninput={(event) => onchange(event.currentTarget.value)}
				/>
			{:else}
				<input
					{id}
					class="ops-input wide"
					type={field.kind === 'secret' && !reveal
						? 'password'
						: field.kind === 'url'
							? 'url'
							: 'text'}
					autocomplete="off"
					spellcheck="false"
					placeholder={field.placeholder}
					{value}
					{disabled}
					aria-describedby={describedBy}
					aria-invalid={error ? 'true' : undefined}
					oninput={(event) => onchange(event.currentTarget.value)}
				/>
				{#if field.kind === 'secret'}
					<button
						type="button"
						class="ops-btn ghost sm"
						aria-pressed={reveal}
						aria-label={reveal ? m.ops_field_hide() : m.ops_field_show()}
						title={reveal ? m.ops_field_hide() : m.ops_field_show()}
						onclick={() => (reveal = !reveal)}
					>
						<Icon path={reveal ? icons.hidden : icons.visible} size={18} />
					</button>
				{/if}
			{/if}
		</div>
		{#if field.hint}<p class="hint" id={hintId}>{field.hint()}</p>{/if}
		{#if error}<p class="error" id={errorId}>{errorText}</p>{/if}
		{#if warning}
			<p class="ops-alert warn"><Icon path={icons.warning} size={18} />{warning}</p>
		{/if}
	</div>
{/if}

<style>
	.field {
		display: grid;
		gap: var(--space-1);
		padding: var(--space-3) 0;
	}

	.switch-field {
		grid-template-columns: minmax(0, 1fr) auto;
		align-items: center;
		gap: var(--space-4);
	}

	.text {
		display: grid;
		gap: 2px;
	}

	label {
		font-weight: 500;
	}

	.dot {
		display: inline-block;
		width: 7px;
		height: 7px;
		margin-left: var(--space-2);
		border-radius: 50%;
		background: var(--color-accent);
		vertical-align: middle;
	}

	.control {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-2) var(--space-3);
	}

	.ops-select {
		min-width: 240px;
		max-width: 100%;
	}

	.number {
		width: 140px;
		font-variant-numeric: tabular-nums;
	}

	.number.short {
		width: 88px;
	}

	.wide {
		flex: 1 1 320px;
		max-width: 560px;
	}

	.range {
		flex: 1 1 200px;
		max-width: 360px;
		accent-color: var(--color-accent);
	}

	.unit {
		color: var(--color-text-muted);
		font-size: var(--font-size-sm);
	}

	.hint,
	.error {
		margin: 0;
		font-size: var(--font-size-xs);
	}

	.hint {
		color: var(--color-text-muted);
	}

	.error {
		color: var(--color-danger);
		font-weight: 600;
	}

	.ops-alert {
		margin-top: var(--space-1);
	}

	.switch {
		position: relative;
		width: 40px;
		height: 22px;
		margin: 0;
		border-radius: 999px;
		background: var(--color-border);
		appearance: none;
		cursor: pointer;
		transition: background var(--duration-fast);
	}

	.switch::after {
		content: '';
		position: absolute;
		top: 3px;
		left: 3px;
		width: 16px;
		height: 16px;
		border-radius: 50%;
		background: #fff;
		box-shadow: 0 1px 2px rgb(0 0 0 / 0.3);
		transition: transform var(--duration-fast);
	}

	.switch:checked {
		background: var(--color-accent);
	}

	.switch:checked::after {
		transform: translateX(18px);
	}

	.switch:disabled {
		opacity: 0.5;
		cursor: default;
	}
</style>
