import { describe, expect, it } from 'vitest';
import { parseInline, parseMarkdown } from './markdown.js';

describe('parseInline', () => {
	it('reads bold and web links', () => {
		expect(parseInline('Fix **maps** in [#12](https://github.com/x/y/pull/12).')).toEqual([
			{ kind: 'text', text: 'Fix ' },
			{ kind: 'bold', text: 'maps' },
			{ kind: 'text', text: ' in ' },
			{ kind: 'link', text: '#12', href: 'https://github.com/x/y/pull/12' },
			{ kind: 'text', text: '.' }
		]);
	});

	it('keeps a link that is not http(s) as plain text', () => {
		expect(parseInline('[click](javascript:alert(1))')).toEqual([
			{ kind: 'text', text: '[click](javascript:alert(1))' }
		]);
	});

	it('leaves HTML as text', () => {
		expect(parseInline('<img src=x onerror=alert(1)>')).toEqual([
			{ kind: 'text', text: '<img src=x onerror=alert(1)>' }
		]);
	});
});

describe('parseMarkdown', () => {
	it('splits headings, lists and paragraphs', () => {
		const blocks = parseMarkdown(
			'## Novedades\n\n- Uno\n- **Dos**\n\n1. Primero\n2. Segundo\n\nTexto\nseguido.'
		);
		expect(blocks).toEqual([
			{ kind: 'heading', level: 4, content: [{ kind: 'text', text: 'Novedades' }] },
			{
				kind: 'list',
				ordered: false,
				items: [[{ kind: 'text', text: 'Uno' }], [{ kind: 'bold', text: 'Dos' }]]
			},
			{
				kind: 'list',
				ordered: true,
				items: [[{ kind: 'text', text: 'Primero' }], [{ kind: 'text', text: 'Segundo' }]]
			},
			{ kind: 'paragraph', content: [{ kind: 'text', text: 'Texto seguido.' }] }
		]);
	});
});
