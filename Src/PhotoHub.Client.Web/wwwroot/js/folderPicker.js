window.folderPicker = {
    _handle: null,

    isSupported() {
        return 'showDirectoryPicker' in window;
    },

    async pick() {
        try {
            const handle = await window.showDirectoryPicker({ mode: 'read' });
            this._handle = handle;
            await this._saveHandle(handle);
            await this._deleteMetadataCache(handle.name); // carpeta nueva → invalidar cache
            return handle.name;
        } catch (e) {
            if (e.name === 'AbortError') return null;
            throw e;
        }
    },

    async getStoredName() {
        try {
            const handle = await this._loadHandle();
            if (!handle) return null;
            this._handle = handle;
            return handle.name;
        } catch {
            return null;
        }
    },

    async requestPermission() {
        try {
            const handle = this._handle || await this._loadHandle();
            if (!handle) return false;
            this._handle = handle;
            const perm = await handle.requestPermission({ mode: 'read' });
            return perm === 'granted';
        } catch {
            return false;
        }
    },

    // ── Metadata cache (IndexedDB) ──────────────────────────────────────────

    async loadMetadataCache(folderName) {
        try {
            const db = await this._openDb();
            return new Promise(resolve => {
                const tx = db.transaction('metadata', 'readonly');
                const req = tx.objectStore('metadata').get(folderName);
                req.onsuccess = () => {
                    const entry = req.result;
                    if (!entry) { resolve(null); return; }
                    const ageHours = (Date.now() - entry.cachedAt) / 3_600_000;
                    resolve(ageHours < 24 ? entry.files : null);
                };
                req.onerror = () => resolve(null);
            });
        } catch {
            return null;
        }
    },

    async saveMetadataCache(folderName, files) {
        try {
            const db = await this._openDb();
            return new Promise((resolve, reject) => {
                const tx = db.transaction('metadata', 'readwrite');
                tx.objectStore('metadata').put({ files, cachedAt: Date.now() }, folderName);
                tx.oncomplete = resolve;
                tx.onerror = () => reject(tx.error);
            });
        } catch { }
    },

    async _deleteMetadataCache(folderName) {
        try {
            const db = await this._openDb();
            return new Promise(resolve => {
                const tx = db.transaction('metadata', 'readwrite');
                tx.objectStore('metadata').delete(folderName);
                tx.oncomplete = resolve;
                tx.onerror = resolve;
            });
        } catch { }
    },

    // ── Enumeración progresiva (callback por batch) ──────────────────────────
    // Llama a dotNetRef.invokeMethodAsync('OnFilesBatch', batch, isFinal) cada batchSize ficheros.
    // Usa BFS para procesar subdirectorios sin recursión profunda.

    async enumerateProgressive(dotNetRef, batchSize) {
        try {
            if (!this._handle) this._handle = await this._loadHandle();
            if (!this._handle) {
                await dotNetRef.invokeMethodAsync('OnFilesBatch', [], true);
                return;
            }

            let perm = await this._handle.queryPermission({ mode: 'read' });
            if (perm !== 'granted') {
                perm = await this._handle.requestPermission({ mode: 'read' });
                if (perm !== 'granted') {
                    await dotNetRef.invokeMethodAsync('OnFilesBatch', [], true);
                    return;
                }
            }

            const IMAGE_EXTS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'avif', 'tiff', 'bmp', 'heic', 'heif']);
            const VIDEO_EXTS = new Set(['mp4', 'mov', 'avi', 'mkv', '3gp', 'm4v', 'webm']);

            let batch = [];
            const queue = [{ handle: this._handle, basePath: '' }];

            const flush = async (isFinal) => {
                const toSend = batch.splice(0);
                try {
                    await dotNetRef.invokeMethodAsync('OnFilesBatch', toSend, isFinal);
                } catch {
                    // Componente descartado (navegación): parar
                    queue.length = 0;
                }
            };

            while (queue.length > 0) {
                const { handle: dirHandle, basePath } = queue.shift();

                const entries = [];
                for await (const entry of dirHandle.entries()) entries.push(entry);

                for (const [name, handle] of entries) {
                    if (handle.kind === 'directory') {
                        queue.push({ handle, basePath: basePath ? `${basePath}/${name}` : name });
                    } else if (handle.kind === 'file') {
                        const ext = (name.split('.').pop() ?? '').toLowerCase();
                        const isImage = IMAGE_EXTS.has(ext);
                        if (!isImage && !VIDEO_EXTS.has(ext)) continue;

                        const file = await handle.getFile();
                        batch.push({
                            name,
                            relativePath: basePath ? `${basePath}/${name}` : name,
                            size: file.size,
                            lastModified: file.lastModified,
                            isImage,
                            thumbnailUrl: null
                        });

                        if (batch.length >= batchSize) await flush(false);
                    }
                }
            }

            await flush(true); // final (puede estar vacío si el último flush fue exacto)

        } catch (e) {
            console.error('folderPicker.enumerateProgressive error:', e);
            try { await dotNetRef.invokeMethodAsync('OnFilesBatch', [], true); } catch { }
        }
    },

    // ── Enumerar ficheros ────────────────────────────────────────────────────

    async enumerate() {
        try {
            if (!this._handle) this._handle = await this._loadHandle();
            if (!this._handle) return [];

            let perm = await this._handle.queryPermission({ mode: 'read' });
            if (perm !== 'granted') {
                perm = await this._handle.requestPermission({ mode: 'read' });
                if (perm !== 'granted') return [];
            }

            const files = [];
            await this._collectFiles(this._handle, '', files);
            return files;
        } catch (e) {
            console.error('folderPicker.enumerate error:', e);
            return [];
        }
    },

    async _collectFiles(dirHandle, basePath, results) {
        const IMAGE_EXTS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'avif', 'tiff', 'bmp', 'heic', 'heif']);
        const VIDEO_EXTS = new Set(['mp4', 'mov', 'avi', 'mkv', '3gp', 'm4v', 'webm']);

        const entries = [];
        for await (const entry of dirHandle.entries()) entries.push(entry);

        for (const [name, handle] of entries) {
            if (handle.kind === 'file') {
                const ext = (name.split('.').pop() ?? '').toLowerCase();
                const isImage = IMAGE_EXTS.has(ext);
                if (!isImage && !VIDEO_EXTS.has(ext)) continue;

                const file = await handle.getFile();
                results.push({
                    name,
                    relativePath: basePath ? `${basePath}/${name}` : name,
                    size: file.size,
                    lastModified: file.lastModified,
                    isImage,
                    thumbnailUrl: null
                });
            } else if (handle.kind === 'directory') {
                await this._collectFiles(handle, basePath ? `${basePath}/${name}` : name, results);
            }
        }
    },

    // ── Blob URLs (thumbnails) ───────────────────────────────────────────────

    async getBlobUrl(relativePath) {
        try {
            const fh = await this._getFileHandle(relativePath);
            return URL.createObjectURL(await fh.getFile());
        } catch (e) {
            console.error('folderPicker.getBlobUrl error:', e);
            return null;
        }
    },

    // Genera blob URLs en paralelo para una lista de rutas.
    // Devuelve { relativePath: blobUrl } — las rutas que fallen quedan fuera.
    async getBlobUrlsBatch(relativePaths) {
        if (!this._handle) this._handle = await this._loadHandle();
        if (!this._handle) return {};

        const result = {};
        await Promise.all(relativePaths.map(async rp => {
            try {
                const fh = await this._getFileHandle(rp);
                result[rp] = URL.createObjectURL(await fh.getFile());
            } catch { }
        }));
        return result;
    },

    revokeBlobUrl(url) {
        if (url) URL.revokeObjectURL(url);
    },

    // ── Checksum + bytes ─────────────────────────────────────────────────────

    async computeChecksum(relativePath) {
        try {
            const fh = await this._getFileHandle(relativePath);
            const buffer = await (await fh.getFile()).arrayBuffer();
            const hash = await crypto.subtle.digest('SHA-256', buffer);
            return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('');
        } catch (e) {
            console.error('folderPicker.computeChecksum error:', e);
            return null;
        }
    },

    async readFileBytes(relativePath) {
        try {
            const fh = await this._getFileHandle(relativePath);
            const buffer = await (await fh.getFile()).arrayBuffer();
            return new Uint8Array(buffer);
        } catch (e) {
            console.error('folderPicker.readFileBytes error:', e);
            return null;
        }
    },

    // ── Server existing keys cache ───────────────────────────────────────────

    async loadExistingKeysCache(folderName) {
        try {
            const db = await this._openDb();
            return new Promise(resolve => {
                const tx = db.transaction('metadata', 'readonly');
                const req = tx.objectStore('metadata').get(folderName);
                req.onsuccess = () => {
                    const entry = req.result;
                    if (!entry || !entry.existingKeys) { resolve(null); return; }
                    const ageHours = (Date.now() - entry.cachedAt) / 3_600_000;
                    resolve(ageHours < 24 ? entry.existingKeys : null);
                };
                req.onerror = () => resolve(null);
            });
        } catch {
            return null;
        }
    },

    async saveExistingKeysCache(folderName, keys) {
        try {
            const db = await this._openDb();
            return new Promise((resolve, reject) => {
                const tx = db.transaction('metadata', 'readwrite');
                const store = tx.objectStore('metadata');
                const req = store.get(folderName);
                req.onsuccess = () => {
                    const entry = req.result || { files: [], cachedAt: Date.now() };
                    entry.existingKeys = keys;
                    store.put(entry, folderName);
                };
                tx.oncomplete = resolve;
                tx.onerror = () => reject(tx.error);
            });
        } catch { }
    },

    async getDeviceCacheInfo(folderName) {
        try {
            const db = await this._openDb();
            return new Promise(resolve => {
                const tx = db.transaction('metadata', 'readonly');
                const req = tx.objectStore('metadata').get(folderName);
                req.onsuccess = () => {
                    const entry = req.result;
                    if (!entry) { resolve(null); return; }
                    const ageHours = (Date.now() - entry.cachedAt) / 3_600_000;
                    if (ageHours >= 24) { resolve(null); return; }
                    resolve({
                        fileCount: entry.files?.length ?? 0,
                        existingKeyCount: entry.existingKeys?.length ?? 0,
                        cachedAt: entry.cachedAt
                    });
                };
                req.onerror = () => resolve(null);
            });
        } catch {
            return null;
        }
    },

    async clearDeviceCache(folderName) {
        await this._deleteMetadataCache(folderName);
    },

    // ── Clear ────────────────────────────────────────────────────────────────

    async clear() {
        if (this._handle) await this._deleteMetadataCache(this._handle.name);
        this._handle = null;
        await this._deleteHandle();
    },

    // ── Helpers internos ─────────────────────────────────────────────────────

    async _getFileHandle(relativePath) {
        if (!this._handle) this._handle = await this._loadHandle();
        const parts = relativePath.split('/');
        let current = this._handle;
        for (let i = 0; i < parts.length - 1; i++) {
            current = await current.getDirectoryHandle(parts[i]);
        }
        return current.getFileHandle(parts[parts.length - 1]);
    },

    async _saveHandle(handle) {
        const db = await this._openDb();
        return new Promise((resolve, reject) => {
            const tx = db.transaction('handles', 'readwrite');
            tx.objectStore('handles').put(handle, 'selected');
            tx.oncomplete = resolve;
            tx.onerror = () => reject(tx.error);
        });
    },

    async _loadHandle() {
        const db = await this._openDb();
        return new Promise(resolve => {
            const tx = db.transaction('handles', 'readonly');
            const req = tx.objectStore('handles').get('selected');
            req.onsuccess = () => resolve(req.result || null);
            req.onerror = () => resolve(null);
        });
    },

    async _deleteHandle() {
        const db = await this._openDb();
        return new Promise(resolve => {
            const tx = db.transaction('handles', 'readwrite');
            tx.objectStore('handles').delete('selected');
            tx.oncomplete = resolve;
            tx.onerror = resolve;
        });
    },

    _openDb() {
        return new Promise((resolve, reject) => {
            const req = indexedDB.open('photohub-folder', 2); // v2: añade store 'metadata'
            req.onupgradeneeded = e => {
                const db = e.target.result;
                if (!db.objectStoreNames.contains('handles'))  db.createObjectStore('handles');
                if (!db.objectStoreNames.contains('metadata')) db.createObjectStore('metadata');
            };
            req.onsuccess = e => resolve(e.target.result);
            req.onerror = () => reject(req.error);
        });
    }
};
