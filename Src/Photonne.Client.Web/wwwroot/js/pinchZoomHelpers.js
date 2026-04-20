window.pinchZoomHelpers = {
    _instances: new WeakMap(),

    attach: function (imgEl) {
        if (!imgEl || this._instances.has(imgEl)) return;

        const state = {
            scale: 1,
            tx: 0,
            ty: 0,
            startDistance: 0,
            startScale: 1,
            startTx: 0,
            startTy: 0,
            startMidX: 0,
            startMidY: 0,
            panStartX: 0,
            panStartY: 0,
            isPinching: false,
            isPanning: false,
            lastTapTime: 0,
            lastTapX: 0,
            lastTapY: 0,
        };

        const apply = () => {
            imgEl.style.transform =
                'translate(' + state.tx + 'px, ' + state.ty + 'px) scale(' + state.scale + ')';
            imgEl.style.transformOrigin = 'center center';
        };

        const clamp = () => {
            const parent = imgEl.parentElement;
            if (!parent) return;
            const pb = parent.getBoundingClientRect();
            const w = imgEl.offsetWidth * state.scale;
            const h = imgEl.offsetHeight * state.scale;
            const overflowX = Math.max(0, (w - pb.width) / 2);
            const overflowY = Math.max(0, (h - pb.height) / 2);
            state.tx = Math.max(-overflowX, Math.min(overflowX, state.tx));
            state.ty = Math.max(-overflowY, Math.min(overflowY, state.ty));
        };

        const resetZoom = (animated) => {
            state.scale = 1;
            state.tx = 0;
            state.ty = 0;
            state.isPinching = false;
            state.isPanning = false;
            if (animated) {
                imgEl.style.transition = 'transform 0.25s ease';
                apply();
                setTimeout(() => { imgEl.style.transition = ''; }, 270);
            } else {
                apply();
            }
        };

        const onTouchStart = (e) => {
            if (e.touches.length === 2) {
                e.preventDefault();
                e.stopPropagation();
                const a = e.touches[0];
                const b = e.touches[1];
                const dx = a.clientX - b.clientX;
                const dy = a.clientY - b.clientY;
                state.startDistance = Math.hypot(dx, dy) || 1;
                state.startScale = state.scale;
                state.startMidX = (a.clientX + b.clientX) / 2;
                state.startMidY = (a.clientY + b.clientY) / 2;
                state.startTx = state.tx;
                state.startTy = state.ty;
                state.isPinching = true;
                state.isPanning = false;
            } else if (e.touches.length === 1 && state.scale > 1) {
                e.preventDefault();
                e.stopPropagation();
                state.isPanning = true;
                state.panStartX = e.touches[0].clientX - state.tx;
                state.panStartY = e.touches[0].clientY - state.ty;
            } else if (e.touches.length === 1) {
                const now = Date.now();
                const t = e.touches[0];
                const dx = t.clientX - state.lastTapX;
                const dy = t.clientY - state.lastTapY;
                if (now - state.lastTapTime < 300 && Math.hypot(dx, dy) < 30) {
                    e.preventDefault();
                    e.stopPropagation();
                    if (state.scale > 1) {
                        resetZoom(true);
                    } else {
                        state.scale = 2;
                        state.tx = 0;
                        state.ty = 0;
                        imgEl.style.transition = 'transform 0.25s ease';
                        apply();
                        setTimeout(() => { imgEl.style.transition = ''; }, 270);
                    }
                    state.lastTapTime = 0;
                } else {
                    state.lastTapTime = now;
                    state.lastTapX = t.clientX;
                    state.lastTapY = t.clientY;
                }
            }
        };

        const onTouchMove = (e) => {
            if (state.isPinching && e.touches.length === 2) {
                e.preventDefault();
                e.stopPropagation();
                const a = e.touches[0];
                const b = e.touches[1];
                const dx = a.clientX - b.clientX;
                const dy = a.clientY - b.clientY;
                const dist = Math.hypot(dx, dy) || 1;
                let scale = state.startScale * (dist / state.startDistance);
                scale = Math.max(1, Math.min(5, scale));
                const midX = (a.clientX + b.clientX) / 2;
                const midY = (a.clientY + b.clientY) / 2;
                state.scale = scale;
                state.tx = state.startTx + (midX - state.startMidX);
                state.ty = state.startTy + (midY - state.startMidY);
                clamp();
                apply();
            } else if (state.isPanning && e.touches.length === 1) {
                e.preventDefault();
                e.stopPropagation();
                state.tx = e.touches[0].clientX - state.panStartX;
                state.ty = e.touches[0].clientY - state.panStartY;
                clamp();
                apply();
            }
        };

        const onTouchEnd = (e) => {
            const wasActive = state.isPinching || state.isPanning;
            if (wasActive) e.stopPropagation();
            if (e.touches.length < 2) state.isPinching = false;
            if (e.touches.length === 0) state.isPanning = false;

            if (state.scale <= 1.02) {
                resetZoom(true);
            } else {
                clamp();
                apply();
            }
        };

        // Reset zoom when the underlying src changes (nav to a different asset
        // reuses the same <img> element, so transform state would otherwise persist).
        const onLoad = () => resetZoom(false);

        imgEl.addEventListener('touchstart', onTouchStart, { passive: false });
        imgEl.addEventListener('touchmove', onTouchMove, { passive: false });
        imgEl.addEventListener('touchend', onTouchEnd, { passive: false });
        imgEl.addEventListener('touchcancel', onTouchEnd, { passive: false });
        imgEl.addEventListener('load', onLoad);

        this._instances.set(imgEl, { onTouchStart, onTouchMove, onTouchEnd, onLoad, resetZoom });
    },

    detach: function (imgEl) {
        if (!imgEl) return;
        const h = this._instances.get(imgEl);
        if (!h) return;
        imgEl.removeEventListener('touchstart', h.onTouchStart);
        imgEl.removeEventListener('touchmove', h.onTouchMove);
        imgEl.removeEventListener('touchend', h.onTouchEnd);
        imgEl.removeEventListener('touchcancel', h.onTouchEnd);
        imgEl.removeEventListener('load', h.onLoad);
        imgEl.style.transform = '';
        imgEl.style.transition = '';
        this._instances.delete(imgEl);
    },

    reset: function (imgEl) {
        const h = this._instances.get(imgEl);
        if (h && h.resetZoom) h.resetZoom(false);
    }
};
