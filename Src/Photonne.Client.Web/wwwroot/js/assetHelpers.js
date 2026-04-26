function downloadFileFromBytes(fileName, contentType, bytes) {
    const blob = new Blob([new Uint8Array(bytes)], { type: contentType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
        URL.revokeObjectURL(url);
        document.body.removeChild(a);
    }, 100);
}

function downloadAsset(url, filename) {
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    setTimeout(() => document.body.removeChild(a), 100);
}

window.assetTransition = {
    _rect: null,

    saveOrigin: function (element) {
        if (!element) return;
        var rect = element.getBoundingClientRect();
        this._rect = { top: rect.top, left: rect.left, right: rect.right, bottom: rect.bottom };
    },

    // Helper: looks up the element by [data-asset-id="..."] and saves its bounds.
    // Used by lightweight grid cells (Albums, Folders) that don't pass an
    // ElementReference to .NET — they only know the asset id.
    saveOriginByAssetId: function (assetId) {
        if (!assetId) return;
        var el = document.querySelector('[data-asset-id="' + CSS.escape(assetId) + '"]');
        if (el) this.saveOrigin(el);
    },

    playEnterAnimation: function (element) {
        if (!element) return;
        var rect = this._rect;
        this._rect = null;

        var vw = window.innerWidth;
        var vh = window.innerHeight;

        if (rect && rect.width > 0 && rect.height > 0) {
            element.animate([
                {
                    clipPath: 'inset(' + rect.top + 'px ' + (vw - rect.right) + 'px ' + (vh - rect.bottom) + 'px ' + rect.left + 'px round 8px)',
                    opacity: '0.75'
                },
                {
                    clipPath: 'inset(0px 0px 0px 0px round 0px)',
                    opacity: '1'
                }
            ], { duration: 360, easing: 'cubic-bezier(0.4, 0, 0.2, 1)', fill: 'none' });
        } else {
            element.animate(
                [{ opacity: '0' }, { opacity: '1' }],
                { duration: 220, easing: 'ease-out', fill: 'none' }
            );
        }
    }
};

window.assetGridHelpers = {
    _dotNetRef: null,
    _lastAssetId: null,
    _lastRowItem: null,
    _sentIds: null,
    _moveHandler: null,
    _endHandler: null,
    SIDE_ZONE_PX: 56,

    _getRowAssetIds(el) {
        const container = el?.closest('[data-asset-id]');
        if (!container) return [];
        const grid = container.closest('.timeline-flat-grid, .mud-grid, .media-thumb-grid');
        if (!grid) return [];
        const rect = container.getBoundingClientRect();
        const ids = [];
        grid.querySelectorAll('[data-asset-id]').forEach(c => {
            const r = c.getBoundingClientRect();
            if (r.top < rect.bottom && r.bottom > rect.top) {
                ids.push(c.dataset.assetId);
            }
        });
        return ids;
    },

    suppressNextClick() {
        // Evita el click sintético que genera el navegador tras touchend
        // (long-press o selección táctil) para que no haga doble-toggle en Blazor.
        document.addEventListener('click', (e) => e.stopPropagation(), { once: true, capture: true });
    },

    startDragSelect(dotNetRef, selectMode) {
        this.stopDragSelect();
        this._dotNetRef = dotNetRef;
        this._selectMode = selectMode;
        this._lastAssetId = null;
        this._lastRowItem = null;
        this._sentIds = new Set();

        this._moveHandler = (e) => {
            if (!e.touches.length) return;
            const t = e.touches[0];
            const el = document.elementFromPoint(t.clientX, t.clientY);

            const inSideZone = t.clientX < this.SIDE_ZONE_PX ||
                               t.clientX > window.innerWidth - this.SIDE_ZONE_PX;

            if (inSideZone) {
                // Modo fila: procesar todos los assets de la fila actual
                const item = el?.closest('[data-asset-id]');
                if (!item || item === this._lastRowItem) return;
                this._lastRowItem = item;

                this._getRowAssetIds(el).forEach(id => {
                    if (!this._sentIds.has(id)) {
                        this._sentIds.add(id);
                        dotNetRef.invokeMethodAsync('OnDragSelectAsset', id, this._selectMode);
                    }
                });
            } else {
                // Modo normal: procesar el asset individual bajo el dedo
                const id = el?.closest('[data-asset-id]')?.dataset.assetId;
                if (id && id !== this._lastAssetId) {
                    this._lastAssetId = id;
                    if (!this._sentIds.has(id)) {
                        this._sentIds.add(id);
                        dotNetRef.invokeMethodAsync('OnDragSelectAsset', id, this._selectMode);
                    }
                }
            }
        };

        this._endHandler = () => this.stopDragSelect();

        document.addEventListener('touchmove', this._moveHandler, { passive: true });
        document.addEventListener('touchend', this._endHandler, { once: true });
        document.addEventListener('touchcancel', this._endHandler, { once: true });
    },

    stopDragSelect() {
        if (this._moveHandler) {
            document.removeEventListener('touchmove', this._moveHandler);
            this._moveHandler = null;
        }
        if (this._endHandler) {
            document.removeEventListener('touchend', this._endHandler);
            document.removeEventListener('touchcancel', this._endHandler);
            this._endHandler = null;
        }
        this._dotNetRef = null;
        this._lastAssetId = null;
        this._lastRowItem = null;
        this._sentIds = null;
    }
};

async function shareOrCopyUrl(url, title) {
    if (navigator.share) {
        try {
            await navigator.share({ url: url, title: title });
            return 'shared';
        } catch (e) {
            if (e.name === 'AbortError') return 'aborted';
        }
    }
    try {
        await navigator.clipboard.writeText(url);
        return 'copied';
    } catch {
        return 'error';
    }
}

// Tracks the bounding rect of the visible AssetDetail image and pipes it back
// to a .NET component on every layout change (window resize, image load,
// scroll), so a face-overlay element can be positioned exactly on top.
window.faceOverlayHelpers = {
    _trackers: new Map(),

    _findImage: function () {
        // The AssetDetail view renders a single .asset-detail-image at a time
        // (the image, not the placeholder/icon). Skip <video> tags.
        const candidates = document.querySelectorAll('img.asset-detail-image');
        for (const c of candidates) {
            if (c.tagName === 'IMG' && c.complete) return c;
        }
        return candidates.length > 0 ? candidates[0] : null;
    },

    start: function (key, dotnetRef) {
        this.stop(key);

        const tracker = {
            ref: dotnetRef,
            img: null,
            ro: null,
            onScroll: null,
            onResize: null,
            onLoad: null,
            rafId: null,
        };

        const push = () => {
            if (!tracker.img) return;
            const r = tracker.img.getBoundingClientRect();
            // Filter degenerate rects (0×0) so .NET never renders bogus overlays.
            if (r.width < 4 || r.height < 4) return;
            tracker.ref.invokeMethodAsync('OnRectChanged', r.left, r.top, r.width, r.height);
        };

        const schedulePush = () => {
            if (tracker.rafId) return;
            tracker.rafId = requestAnimationFrame(() => {
                tracker.rafId = null;
                push();
            });
        };

        const attach = (img) => {
            tracker.img = img;
            tracker.onLoad = () => schedulePush();
            img.addEventListener('load', tracker.onLoad);

            if ('ResizeObserver' in window) {
                tracker.ro = new ResizeObserver(() => schedulePush());
                tracker.ro.observe(img);
            }
            schedulePush();
        };

        // Observe DOM until the image element exists.
        const findAndAttach = () => {
            const img = this._findImage();
            if (img) attach(img);
            else setTimeout(findAndAttach, 80);
        };
        findAndAttach();

        tracker.onResize = () => schedulePush();
        tracker.onScroll = () => schedulePush();
        window.addEventListener('resize', tracker.onResize);
        window.addEventListener('scroll', tracker.onScroll, true);

        this._trackers.set(key, tracker);
    },

    refresh: function (key) {
        const t = this._trackers.get(key);
        if (!t || !t.img) return;
        const r = t.img.getBoundingClientRect();
        if (r.width >= 4 && r.height >= 4) {
            t.ref.invokeMethodAsync('OnRectChanged', r.left, r.top, r.width, r.height);
        }
    },

    stop: function (key) {
        const t = this._trackers.get(key);
        if (!t) return;
        if (t.img && t.onLoad) t.img.removeEventListener('load', t.onLoad);
        if (t.ro && t.img) t.ro.unobserve(t.img);
        if (t.ro) t.ro.disconnect();
        if (t.onResize) window.removeEventListener('resize', t.onResize);
        if (t.onScroll) window.removeEventListener('scroll', t.onScroll, true);
        if (t.rafId) cancelAnimationFrame(t.rafId);
        this._trackers.delete(key);
    },
};
