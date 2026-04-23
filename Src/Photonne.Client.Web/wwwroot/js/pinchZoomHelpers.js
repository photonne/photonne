window.pinchZoomHelpers = {
    _instances: new WeakMap(),

    attach: function (imgEl) {
        if (!imgEl || this._instances.has(imgEl)) return;

        const MIN_SCALE = 1;
        const MAX_SCALE = 5;
        const DOUBLE_TAP_SCALE = 2;
        const DOUBLE_TAP_MS = 300;
        const DOUBLE_TAP_RADIUS = 30;
        const PAN_START_THRESHOLD = 6;

        const state = {
            scale: 1,
            tx: 0,
            ty: 0,
            // Pinch
            isPinching: false,
            pinchStartDistance: 0,
            pinchStartScale: 1,
            pinchAnchorImgX: 0,
            pinchAnchorImgY: 0,
            // Pan
            isPanning: false,
            panStartClientX: 0,
            panStartClientY: 0,
            panStartTx: 0,
            panStartTy: 0,
            // Single-finger pending (may become tap, double-tap or pan)
            pendingTouch: false,
            pendingClientX: 0,
            pendingClientY: 0,
            // Last tap tracking
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

        // Convert a client (viewport) coordinate to a coordinate relative to the
        // image's untransformed center. Used as an anchor that stays stable while scaling.
        const clientToImageCenterOffset = (clientX, clientY) => {
            const r = imgEl.getBoundingClientRect();
            const cx = r.left + r.width / 2;
            const cy = r.top + r.height / 2;
            return { x: (clientX - cx) / state.scale, y: (clientY - cy) / state.scale };
        };

        // Zoom to `newScale` keeping the given client point anchored on the image.
        const zoomTo = (newScale, clientX, clientY, animated) => {
            newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
            const anchor = clientToImageCenterOffset(clientX, clientY);
            // Current world-space offset of the anchor from viewport center.
            const currentOffsetX = anchor.x * state.scale + state.tx;
            const currentOffsetY = anchor.y * state.scale + state.ty;
            state.scale = newScale;
            state.tx = currentOffsetX - anchor.x * newScale;
            state.ty = currentOffsetY - anchor.y * newScale;
            clamp();
            if (animated) {
                imgEl.style.transition = 'transform 0.25s ease';
                apply();
                setTimeout(() => { imgEl.style.transition = ''; }, 270);
            } else {
                apply();
            }
        };

        const resetZoom = (animated) => {
            state.scale = 1;
            state.tx = 0;
            state.ty = 0;
            state.isPinching = false;
            state.isPanning = false;
            state.pendingTouch = false;
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
                state.pinchStartDistance = Math.hypot(dx, dy) || 1;
                state.pinchStartScale = state.scale;
                const midX = (a.clientX + b.clientX) / 2;
                const midY = (a.clientY + b.clientY) / 2;
                const anchor = clientToImageCenterOffset(midX, midY);
                state.pinchAnchorImgX = anchor.x;
                state.pinchAnchorImgY = anchor.y;
                state.isPinching = true;
                state.isPanning = false;
                state.pendingTouch = false;
                return;
            }

            if (e.touches.length === 1) {
                const t = e.touches[0];
                const now = Date.now();
                const dxTap = t.clientX - state.lastTapX;
                const dyTap = t.clientY - state.lastTapY;
                const isDoubleTap = now - state.lastTapTime < DOUBLE_TAP_MS
                                    && Math.hypot(dxTap, dyTap) < DOUBLE_TAP_RADIUS;

                if (isDoubleTap) {
                    e.preventDefault();
                    e.stopPropagation();
                    if (state.scale > 1.02) {
                        resetZoom(true);
                    } else {
                        zoomTo(DOUBLE_TAP_SCALE, t.clientX, t.clientY, true);
                    }
                    state.lastTapTime = 0;
                    state.pendingTouch = false;
                    state.isPanning = false;
                    return;
                }

                state.lastTapTime = now;
                state.lastTapX = t.clientX;
                state.lastTapY = t.clientY;

                // Defer pan start until first real movement so a tap can still
                // be promoted to a double-tap if the second finger arrives in time.
                state.pendingTouch = true;
                state.pendingClientX = t.clientX;
                state.pendingClientY = t.clientY;
                state.panStartClientX = t.clientX;
                state.panStartClientY = t.clientY;
                state.panStartTx = state.tx;
                state.panStartTy = state.ty;
                state.isPanning = false;

                if (state.scale > 1) {
                    // Block page-level swipe when zoomed in (pan will handle motion).
                    e.stopPropagation();
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
                let scale = state.pinchStartScale * (dist / state.pinchStartDistance);
                scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
                const midX = (a.clientX + b.clientX) / 2;
                const midY = (a.clientY + b.clientY) / 2;
                // Keep the anchor point (captured on touchstart in image-local
                // coords) pinned under the fingers' midpoint:
                //   midX = CX0 + tx' + anchor.x * scale'
                // where CX0 is the image layout-center when tx=0 (frame-invariant).
                const r = imgEl.getBoundingClientRect();
                const cx0 = r.left + r.width / 2 - state.tx;
                const cy0 = r.top + r.height / 2 - state.ty;
                state.scale = scale;
                state.tx = midX - cx0 - state.pinchAnchorImgX * scale;
                state.ty = midY - cy0 - state.pinchAnchorImgY * scale;
                clamp();
                apply();
                return;
            }

            if (e.touches.length === 1) {
                const t = e.touches[0];

                if (state.pendingTouch && !state.isPanning) {
                    const moved = Math.hypot(
                        t.clientX - state.pendingClientX,
                        t.clientY - state.pendingClientY
                    );
                    if (moved > PAN_START_THRESHOLD && state.scale > 1) {
                        state.isPanning = true;
                        state.pendingTouch = false;
                    } else if (moved > PAN_START_THRESHOLD) {
                        // Movement without zoom: let the outer swipe handler take over.
                        state.pendingTouch = false;
                    }
                }

                if (state.isPanning) {
                    e.preventDefault();
                    e.stopPropagation();
                    state.tx = state.panStartTx + (t.clientX - state.panStartClientX);
                    state.ty = state.panStartTy + (t.clientY - state.panStartClientY);
                    clamp();
                    apply();
                }
            }
        };

        const onTouchEnd = (e) => {
            const wasActive = state.isPinching || state.isPanning;
            if (wasActive) e.stopPropagation();

            const wasPinching = state.isPinching;
            if (e.touches.length < 2) state.isPinching = false;
            if (e.touches.length === 0) {
                state.isPanning = false;
                state.pendingTouch = false;
            }

            // When a pinch ends with one finger still down, hand off seamlessly
            // to pan so the user doesn't need to lift and re-touch.
            if (wasPinching && e.touches.length === 1 && state.scale > 1) {
                const t = e.touches[0];
                state.isPanning = true;
                state.panStartClientX = t.clientX;
                state.panStartClientY = t.clientY;
                state.panStartTx = state.tx;
                state.panStartTy = state.ty;
            }

            if (state.scale <= 1.02) {
                // Snap back if user pinched below 1x.
                if (state.scale !== 1 || state.tx !== 0 || state.ty !== 0) {
                    resetZoom(true);
                }
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

        this._instances.set(imgEl, {
            onTouchStart, onTouchMove, onTouchEnd, onLoad,
            resetZoom,
            getScale: () => state.scale,
            isZoomed: () => state.scale > 1.02,
        });
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
    },

    isZoomed: function (imgEl) {
        const h = this._instances.get(imgEl);
        return !!(h && h.isZoomed && h.isZoomed());
    },

    getScale: function (imgEl) {
        const h = this._instances.get(imgEl);
        return h && h.getScale ? h.getScale() : 1;
    }
};
