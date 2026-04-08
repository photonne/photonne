window.thumbnailCache = {
    CACHE_NAME: 'photonne-thumbnails-v1',

    getCount: async function () {
        if (!('caches' in window)) return 0;
        try {
            const cache = await caches.open(this.CACHE_NAME);
            const keys = await cache.keys();
            return keys.length;
        } catch {
            return 0;
        }
    },

    getInfo: async function () {
        if (!('caches' in window)) return { count: 0, size: 0 };
        try {
            const cache = await caches.open(this.CACHE_NAME);
            const keys = await cache.keys();
            let size = 0;
            for (const req of keys) {
                const res = await cache.match(req);
                if (res) {
                    const cl = res.headers.get('content-length');
                    if (cl) {
                        size += parseInt(cl, 10);
                    } else {
                        const blob = await res.blob();
                        size += blob.size;
                    }
                }
            }
            return { count: keys.length, size };
        } catch {
            return { count: 0, size: 0 };
        }
    },

    clear: async function () {
        if (!('caches' in window)) return;
        await caches.delete(this.CACHE_NAME);
    }
};
