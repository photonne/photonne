/**
 * A minimal reader for release notes (GitHub Markdown): headings, bullet and
 * numbered lists, paragraphs, **bold** and [links](https://…). It returns
 * plain data that the page renders with ordinary elements, so nothing from
 * the notes ever reaches the DOM as HTML. Anything else (code, tables,
 * images…) stays as its text.
 */

export type Inline =
	| { kind: 'text'; text: string }
	| { kind: 'bold'; text: string }
	| { kind: 'link'; text: string; href: string };

export type Block =
	| { kind: 'heading'; level: 3 | 4 | 5; content: Inline[] }
	| { kind: 'list'; ordered: boolean; items: Inline[][] }
	| { kind: 'paragraph'; content: Inline[] };

const HEADING = /^(#{1,6})\s+(.*?)\s*#*\s*$/;
const BULLET = /^\s*[-*+]\s+(.*)$/;
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/;
const INLINE = /\*\*(.+?)\*\*|__(.+?)__|\[([^\]]+)\]\(([^)\s]+)\)|<(https?:\/\/[^>\s]+)>/g;

/** Only web links: a `javascript:` or `data:` URL in the notes stays text. */
function safeHref(href: string) {
	return /^https?:\/\//i.test(href) ? href : null;
}

export function parseInline(text: string): Inline[] {
	const out: Inline[] = [];
	let last = 0;
	const push = (piece: Inline) => {
		const previous = out.at(-1);
		if (piece.kind === 'text' && previous?.kind === 'text') previous.text += piece.text;
		else if (piece.kind !== 'text' || piece.text) out.push(piece);
	};
	for (const match of text.matchAll(INLINE)) {
		push({ kind: 'text', text: text.slice(last, match.index) });
		const [whole, bold, boldAlt, label, href, autolink] = match;
		if (bold ?? boldAlt) {
			push({ kind: 'bold', text: bold ?? boldAlt });
		} else if (autolink) {
			push({ kind: 'link', text: autolink, href: autolink });
		} else {
			const safe = safeHref(href);
			push(safe ? { kind: 'link', text: label, href: safe } : { kind: 'text', text: whole });
		}
		last = match.index + whole.length;
	}
	push({ kind: 'text', text: text.slice(last) });
	return out;
}

export function parseMarkdown(source: string): Block[] {
	const blocks: Block[] = [];
	let paragraph: string[] = [];
	let list: { ordered: boolean; items: Inline[][] } | null = null;

	const flushParagraph = () => {
		if (paragraph.length)
			blocks.push({ kind: 'paragraph', content: parseInline(paragraph.join(' ')) });
		paragraph = [];
	};
	const flushList = () => {
		if (list) blocks.push({ kind: 'list', ...list });
		list = null;
	};

	for (const raw of source.replace(/\r\n?/g, '\n').split('\n')) {
		const line = raw.trimEnd();
		if (!line.trim()) {
			flushParagraph();
			flushList();
			continue;
		}
		const heading = HEADING.exec(line);
		if (heading) {
			flushParagraph();
			flushList();
			// The section's own h2/h3 sit above: the notes' headings start below them.
			const level = Math.min(5, Math.max(3, heading[1].length + 2)) as 3 | 4 | 5;
			blocks.push({ kind: 'heading', level, content: parseInline(heading[2]) });
			continue;
		}
		const bullet = BULLET.exec(line);
		const numbered = bullet ? null : NUMBERED.exec(line);
		if (bullet || numbered) {
			flushParagraph();
			const ordered = !!numbered;
			if (list && list.ordered !== ordered) flushList();
			list ??= { ordered, items: [] };
			list.items.push(parseInline((bullet ?? numbered)![1]));
			continue;
		}
		if (list && /^\s+/.test(raw)) {
			// A wrapped list item.
			const item = list.items.at(-1)!;
			item.push({ kind: 'text', text: ' ' }, ...parseInline(line.trim()));
			continue;
		}
		flushList();
		paragraph.push(line.trim());
	}
	flushParagraph();
	flushList();
	return blocks;
}
