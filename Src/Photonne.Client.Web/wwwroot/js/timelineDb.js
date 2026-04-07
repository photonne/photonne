window.timelineDb = {
    DB_NAME: 'photonne-timeline',
    DB_VERSION: 1,
    MAX_SECTIONS: 24,

    _db: null,

    async _open() {
        if (this._db) return this._db;
        return new Promise((resolve, reject) => {
            const req = indexedDB.open(this.DB_NAME, this.DB_VERSION);
            req.onupgradeneeded = e => {
                const db = e.target.result;
                if (!db.objectStoreNames.contains('index')) {
                    db.createObjectStore('index');
                }
                if (!db.objectStoreNames.contains('sections')) {
                    db.createObjectStore('sections');
                }
            };
            req.onsuccess = e => {
                this._db = e.target.result;
                resolve(this._db);
            };
            req.onerror = () => reject(req.error);
        });
    },

    // Devuelve el array de TimelineIndexItem o null si no hay nada guardado
    async loadIndex() {
        try {
            const db = await this._open();
            return new Promise(resolve => {
                const tx = db.transaction('index', 'readonly');
                const req = tx.objectStore('index').get('main');
                req.onsuccess = () => resolve(req.result ? req.result.items : null);
                req.onerror = () => resolve(null);
            });
        } catch {
            return null;
        }
    },

    async saveIndex(items) {
        try {
            const db = await this._open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction('index', 'readwrite');
                tx.objectStore('index').put({ items, savedAt: Date.now() }, 'main');
                tx.oncomplete = resolve;
                tx.onerror = () => reject(tx.error);
            });
        } catch { }
    },

    // Devuelve el array de TimelineItem para el mes dado o null
    async loadSection(yearMonth) {
        try {
            const db = await this._open();
            return new Promise(resolve => {
                const tx = db.transaction('sections', 'readonly');
                const req = tx.objectStore('sections').get(yearMonth);
                req.onsuccess = () => resolve(req.result ? req.result.items : null);
                req.onerror = () => resolve(null);
            });
        } catch {
            return null;
        }
    },

    async saveSection(yearMonth, items) {
        try {
            const db = await this._open();
            await new Promise((resolve, reject) => {
                const tx = db.transaction('sections', 'readwrite');
                tx.objectStore('sections').put({ items, savedAt: Date.now() }, yearMonth);
                tx.oncomplete = resolve;
                tx.onerror = () => reject(tx.error);
            });
            await this._trimOldSections();
        } catch { }
    },

    async clearAll() {
        try {
            const db = await this._open();
            return new Promise(resolve => {
                const tx = db.transaction(['index', 'sections'], 'readwrite');
                tx.objectStore('index').clear();
                tx.objectStore('sections').clear();
                tx.oncomplete = resolve;
                tx.onerror = resolve;
            });
        } catch { }
    },

    // Elimina las entradas más antiguas si se supera MAX_SECTIONS
    async _trimOldSections() {
        try {
            const db = await this._open();
            return new Promise(resolve => {
                const tx = db.transaction('sections', 'readwrite');
                const store = tx.objectStore('sections');
                const req = store.getAllKeys();
                req.onsuccess = () => {
                    const keys = req.result;
                    if (keys.length <= this.MAX_SECTIONS) { resolve(); return; }
                    // Los keys son "yyyy-MM"; ordenar descendente y borrar los más antiguos
                    keys.sort().reverse();
                    const toDelete = keys.slice(this.MAX_SECTIONS);
                    for (const key of toDelete) {
                        store.delete(key);
                    }
                    tx.oncomplete = resolve;
                    tx.onerror = resolve;
                };
                req.onerror = () => resolve();
            });
        } catch { }
    }
};
