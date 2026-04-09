window.timelineDb = {
    DB_NAME: 'photonne-timeline',
    DB_VERSION: 2,

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
                // v2: grid store replaces per-section storage
                if (!db.objectStoreNames.contains('grid')) {
                    db.createObjectStore('grid');
                }
                // Remove legacy sections store if present
                if (db.objectStoreNames.contains('sections')) {
                    db.deleteObjectStore('sections');
                }
            };
            req.onsuccess = e => {
                this._db = e.target.result;
                resolve(this._db);
            };
            req.onerror = () => reject(req.error);
        });
    },

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

    async loadGrid() {
        try {
            const db = await this._open();
            return new Promise(resolve => {
                const tx = db.transaction('grid', 'readonly');
                const req = tx.objectStore('grid').get('main');
                req.onsuccess = () => resolve(req.result ? req.result.items : null);
                req.onerror = () => resolve(null);
            });
        } catch {
            return null;
        }
    },

    async saveGrid(items) {
        try {
            const db = await this._open();
            return new Promise((resolve, reject) => {
                const tx = db.transaction('grid', 'readwrite');
                tx.objectStore('grid').put({ items, savedAt: Date.now() }, 'main');
                tx.oncomplete = resolve;
                tx.onerror = () => reject(tx.error);
            });
        } catch { }
    },

    async clearAll() {
        try {
            const db = await this._open();
            var stores = ['index', 'grid'].filter(s => db.objectStoreNames.contains(s));
            if (stores.length === 0) return;
            return new Promise(resolve => {
                const tx = db.transaction(stores, 'readwrite');
                for (var s of stores) tx.objectStore(s).clear();
                tx.oncomplete = resolve;
                tx.onerror = resolve;
            });
        } catch { }
    }
};
