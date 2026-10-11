import { describe, expect, it } from 'vitest';
import { m } from '#lib/paraglide/messages.js';
import { parameterChips } from './labels.js';

describe('parameterChips', () => {
	it('names the switches that are on and hides the rest', () => {
		expect(
			parameterChips({
				kind: 'purge-missing',
				dryRun: 'True',
				fromFile: 'False',
				writeToFile: 'true'
			})
		).toEqual([m.ops_param_dry_run(), m.ops_param_write_file()]);
	});

	it('never shows ids or keys it does not know', () => {
		expect(parameterChips({ libraryId: '3f2c1a9e-8b7d-4c6e-9f10-2a3b4c5d6e7f' })).toEqual([]);
		expect(parameterChips({ somethingNew: 'True' })).toEqual([]);
		expect(parameterChips(null)).toEqual([]);
	});
});
