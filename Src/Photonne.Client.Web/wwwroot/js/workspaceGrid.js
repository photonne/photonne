// Interop de la rejilla del workspace (/fotos):
//  - ResizeObserver del contenedor -> OnContainerResized(width)
//  - IntersectionObserver con margen de prefetch -> OnBucketApproaching(key)
//    cuando una sección de mes se acerca al viewport (hidratación bajo demanda)
//  - compensación de scroll cuando un mes por ENCIMA del viewport cambia de
//    altura estimada a exacta (el contenido visible no debe saltar)
window.workspaceGrid = (() => {
    const states = new Map();
    let nextId = 1;

    // Traga el click sintético que sigue a un arrastre de pintado para que no
    // haga un toggle extra en Blazor. Auto-limpieza por si no llega ninguno.
    function suppressNextClick() {
        const handler = e => { e.stopPropagation(); e.preventDefault(); };
        document.addEventListener('click', handler, { once: true, capture: true });
        setTimeout(() => document.removeEventListener('click', handler, { capture: true }), 400);
    }

    return {
        init(container, dotnetRef) {
            const id = nextId++;
            const st = {
                container,
                dotnetRef,
                observed: new WeakSet(),
                // Atajos de la rejilla: Escape limpia, Supr manda a la papelera,
                // F alterna favorito — solo sin el visor abierto y fuera de
                // campos de texto (ahí las teclas son del campo).
                keyHandler: e => {
                    if (document.querySelector('.asset-viewer')) return;
                    if (e.target.closest?.('input, textarea, [contenteditable]')) return;
                    if (e.key === 'Escape') {
                        dotnetRef.invokeMethodAsync('OnEscapePressed');
                    } else if (e.key === 'Delete' || e.key === 'Backspace') {
                        dotnetRef.invokeMethodAsync('OnGridShortcut', 'delete');
                    } else if (e.key === 'f' || e.key === 'F') {
                        dotnetRef.invokeMethodAsync('OnGridShortcut', 'favorite');
                    }
                },
                // Pintado con ratón: arrastrar desde una celda selecciona (o
                // deselecciona, según el estado de la celda inicial — lo
                // decide .NET) todas las celdas por las que pasa el puntero.
                // Con modificadores no arranca: Shift/Ctrl son clic de rango/toggle.
                mouseDownHandler: e => {
                    if (e.button !== 0 || e.shiftKey || e.ctrlKey || e.metaKey) return;
                    const cell = e.target.closest('[data-asset-id]');
                    if (!cell) return;
                    const startId = cell.dataset.assetId;
                    const sx = e.clientX, sy = e.clientY;
                    let active = false;
                    const sent = new Set();
                    const move = ev => {
                        if (!active) {
                            if (Math.hypot(ev.clientX - sx, ev.clientY - sy) < 6) return;
                            active = true;
                            sent.add(startId);
                            dotnetRef.invokeMethodAsync('OnPaintSelectStart', startId);
                            document.body.classList.add('pg-painting');
                        }
                        ev.preventDefault();
                        const over = document.elementFromPoint(ev.clientX, ev.clientY)?.closest('[data-asset-id]');
                        const overId = over?.dataset.assetId;
                        if (overId && !sent.has(overId)) {
                            sent.add(overId);
                            dotnetRef.invokeMethodAsync('OnPaintSelect', overId);
                        }
                    };
                    const up = () => {
                        document.removeEventListener('mousemove', move);
                        document.removeEventListener('mouseup', up);
                        if (active) {
                            document.body.classList.remove('pg-painting');
                            suppressNextClick();
                        }
                    };
                    document.addEventListener('mousemove', move);
                    document.addEventListener('mouseup', up);
                },
                io: new IntersectionObserver(entries => {
                    for (const e of entries) {
                        if (e.isIntersecting) {
                            dotnetRef.invokeMethodAsync('OnBucketApproaching', e.target.dataset.bucket);
                        }
                    }
                }, { rootMargin: '1200px 0px' }),
                ro: new ResizeObserver(entries => {
                    const w = entries[0]?.contentRect?.width;
                    if (w) dotnetRef.invokeMethodAsync('OnContainerResized', w);
                })
            };
            st.ro.observe(container);
            document.addEventListener('keydown', st.keyHandler);
            container.addEventListener('mousedown', st.mouseDownHandler);
            states.set(id, st);
            return id;
        },

        // Idempotente: registra en el IntersectionObserver las secciones aún
        // no observadas. Se llama tras el primer render con buckets.
        observeSections(id) {
            const st = states.get(id);
            if (!st) return;
            st.container.querySelectorAll('[data-bucket]').forEach(el => {
                if (!st.observed.has(el)) {
                    st.observed.add(el);
                    st.io.observe(el);
                }
            });
        },

        // Si la sección hidratada quedó entera por encima del viewport, el
        // delta de altura empujaría el contenido visible: se compensa el scroll.
        compensateIfAbove(id, key, delta) {
            const st = states.get(id);
            if (!st || !delta) return;
            const el = st.container.querySelector(`[data-bucket="${CSS.escape(key)}"]`);
            if (!el) return;
            if (el.getBoundingClientRect().bottom < 0) {
                (document.scrollingElement || document.documentElement).scrollTop += delta;
            }
        },

        // Variante para colecciones planas (álbum, carpeta, papelera, búsqueda):
        // mismo resize/pintado/teclas, y en vez de observar buckets observa un
        // único centinela [data-loadmore] para la paginación.
        initFlat(container, dotnetRef) {
            const id = this.init(container, dotnetRef);
            const st = states.get(id);
            st.io.disconnect();
            st.io = new IntersectionObserver(entries => {
                if (entries.some(e => e.isIntersecting)) {
                    dotnetRef.invokeMethodAsync('OnLoadMoreVisible');
                }
            }, { rootMargin: '900px 0px' });
            return id;
        },

        observeSentinel(id) {
            const st = states.get(id);
            if (!st) return;
            const sentinel = st.container.querySelector('[data-loadmore]');
            if (sentinel && !st.observed.has(sentinel)) {
                st.observed.add(sentinel);
                st.io.observe(sentinel);
            }
        },

        dispose(id) {
            const st = states.get(id);
            if (st) {
                st.io.disconnect();
                st.ro.disconnect();
                document.removeEventListener('keydown', st.keyHandler);
                st.container.removeEventListener('mousedown', st.mouseDownHandler);
                states.delete(id);
            }
        }
    };
})();
