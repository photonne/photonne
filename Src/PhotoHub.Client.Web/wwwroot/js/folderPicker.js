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

        // Recoger primero todos los entries para poder procesarlos sin bloquear
        const entries = [];
        for await (const entry of dirHandle.entries()) {
            entries.push(entry);
        }

        for (const [name, handle] of entries) {
            if (handle.kind === 'file') {
                const ext = (name.split('.').pop() ?? '').toLowerCase();
                const isImage = IMAGE_EXTS.has(ext);
                const isVideo = VIDEO_EXTS.has(ext);
                if (!isImage && !isVideo) continue;

                const file = await handle.getFile();
                const relativePath = basePath ? `${basePath}/${name}` : name;

                results.push({
                    name,
                    relativePath,
                    size: file.size,
                    lastModified: file.lastModified,
                    isImage,
                    thumbnailUrl: null
                });
            } else if (handle.kind === 'directory') {
                const subPath = basePath ? `${basePath}/${name}` : name;
                await this._collectFiles(handle, subPath, results);
            }
        }
    },

    async getBlobUrl(relativePath) {
        try {
            if (!this._handle) this._handle = await this._loadHandle();
            if (!this._handle) return null;

            const parts = relativePath.split('/');
            let current = this._handle;
            for (let i = 0; i < parts.length - 1; i++) {
                current = await current.getDirectoryHandle(parts[i]);
            }
            const fileHandle = await current.getFileHandle(parts[parts.length - 1]);
            const file = await fileHandle.getFile();
            return URL.createObjectURL(file);
        } catch (e) {
            console.error('folderPicker.getBlobUrl error:', e);
            return null;
        }
    },

    revokeBlobUrl(url) {
        if (url) URL.revokeObjectURL(url);
    },

    async computeChecksum(relativePath) {
        try {
            if (!this._handle) this._handle = await this._loadHandle();
            if (!this._handle) return null;

            const parts = relativePath.split('/');
            let current = this._handle;
            for (let i = 0; i < parts.length - 1; i++) {
                current = await current.getDirectoryHandle(parts[i]);
            }
            const fileHandle = await current.getFileHandle(parts[parts.length - 1]);
            const file = await fileHandle.getFile();
            const buffer = await file.arrayBuffer();
            const hashBuffer = await crypto.subtle.digest('SHA-256', buffer);
            return Array.from(new Uint8Array(hashBuffer))
                .map(b => b.toString(16).padStart(2, '0'))
                .join('');
        } catch (e) {
            console.error('folderPicker.computeChecksum error:', e);
            return null;
        }
    },

    async readFileBytes(relativePath) {
        try {
            if (!this._handle) this._handle = await this._loadHandle();
            if (!this._handle) return null;

            const parts = relativePath.split('/');
            let current = this._handle;
            for (let i = 0; i < parts.length - 1; i++) {
                current = await current.getDirectoryHandle(parts[i]);
            }
            const fileHandle = await current.getFileHandle(parts[parts.length - 1]);
            const file = await fileHandle.getFile();
            const buffer = await file.arrayBuffer();
            return new Uint8Array(buffer);
        } catch (e) {
            console.error('folderPicker.readFileBytes error:', e);
            return null;
        }
    },

    async clear() {
        this._handle = null;
        await this._deleteHandle();
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
        return new Promise((resolve) => {
            const tx = db.transaction('handles', 'readonly');
            const req = tx.objectStore('handles').get('selected');
            req.onsuccess = () => resolve(req.result || null);
            req.onerror = () => resolve(null);
        });
    },

    async _deleteHandle() {
        const db = await this._openDb();
        return new Promise((resolve) => {
            const tx = db.transaction('handles', 'readwrite');
            tx.objectStore('handles').delete('selected');
            tx.oncomplete = resolve;
            tx.onerror = resolve;
        });
    },

    _openDb() {
        return new Promise((resolve, reject) => {
            const req = indexedDB.open('photohub-folder', 1);
            req.onupgradeneeded = e => e.target.result.createObjectStore('handles');
            req.onsuccess = e => resolve(e.target.result);
            req.onerror = () => reject(req.error);
        });
    }
};
