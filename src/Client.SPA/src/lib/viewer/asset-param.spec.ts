import { describe, expect, it } from 'vitest';
import { withAssetParam } from './asset-param.js';

describe('withAssetParam', () => {
	it('sets, replaces and removes the asset, keeping other parameters', () => {
		expect(withAssetParam('https://x.test/search?q=mar', 'a1')).toBe('/search?q=mar&asset=a1');
		expect(withAssetParam('https://x.test/?asset=a1', 'b2')).toBe('/?asset=b2');
		expect(withAssetParam('https://x.test/search?q=mar&asset=a1', null)).toBe('/search?q=mar');
	});
});
