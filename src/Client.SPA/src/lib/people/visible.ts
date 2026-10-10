/**
 * `{@attach whenVisible(load)}`: calls `load` whenever the element scrolls
 * within `margin` of the viewport (the end-of-list sentinel of a paged grid).
 */
export function whenVisible(load: () => void, margin = '600px') {
	return (node: HTMLElement) => {
		const observer = new IntersectionObserver(
			(entries) => {
				if (entries.some((entry) => entry.isIntersecting)) load();
			},
			{ rootMargin: margin }
		);
		observer.observe(node);
		return () => observer.disconnect();
	};
}
