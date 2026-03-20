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
        const item = el?.closest('.masonry-item');
        if (!item) return [];
        const grid = item.closest('.masonry-grid');
        if (!grid) return [];
        const rect = item.getBoundingClientRect();
        const ids = [];
        grid.querySelectorAll('[data-asset-id]').forEach(container => {
            const r = container.getBoundingClientRect();
            if (r.top < rect.bottom && r.bottom > rect.top) {
                ids.push(container.dataset.assetId);
            }
        });
        return ids;
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
                const item = el?.closest('.masonry-item');
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
