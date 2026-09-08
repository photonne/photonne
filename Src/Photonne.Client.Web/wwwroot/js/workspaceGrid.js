// Interop de la rejilla del workspace (/fotos):
//  - ResizeObserver del contenedor -> OnContainerResized(width)
//  - IntersectionObserver con margen de prefetch -> OnBucketApproaching(key)
//    cuando una sección de mes se acerca al viewport (hidratación bajo demanda)
//  - compensación de scroll cuando un mes por ENCIMA del viewport cambia de
//    altura estimada a exacta (el contenido visible no debe saltar)
window.workspaceGrid = (() => {
    const states = new Map();
    let nextId = 1;

    return {
        init(container, dotnetRef) {
            const id = nextId++;
            const st = {
                container,
                dotnetRef,
                observed: new WeakSet(),
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

        dispose(id) {
            const st = states.get(id);
            if (st) {
                st.io.disconnect();
                st.ro.disconnect();
                states.delete(id);
            }
        }
    };
})();
