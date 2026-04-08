window.pwaInstall = {
    _dotNetHelper: null,
    _deferredPrompt: null,
    _available: false,

    _isStandalone: function () {
        return window.matchMedia('(display-mode: standalone)').matches
            || window.matchMedia('(display-mode: window-controls-overlay)').matches
            || window.navigator.standalone === true;
    },

    init: function (dotNetHelper) {
        this._dotNetHelper = dotNetHelper;

        // Already installed — nothing to show
        if (this._isStandalone()) return;

        // Pick up the event captured early in index.html (before Blazor loaded)
        if (window.__pwaInstallPrompt) {
            this._deferredPrompt = window.__pwaInstallPrompt;
            window.__pwaInstallPrompt = null;
            this._notifyAvailability(true);
        }

        // Also listen for future firings (e.g. after dismissal criteria reset)
        window.addEventListener('beforeinstallprompt', (e) => {
            e.preventDefault();
            this._deferredPrompt = e;
            this._notifyAvailability(true);
        });

        window.addEventListener('appinstalled', () => {
            this._deferredPrompt = null;
            window.__pwaInstallPrompt = null;
            this._notifyAvailability(false);
        });
    },

    _notifyAvailability: function (available) {
        if (this._available === available) return;
        this._available = available;
        if (this._dotNetHelper) {
            this._dotNetHelper.invokeMethodAsync('SetInstallAvailability', available);
        }
    },

    promptInstall: async function () {
        if (!this._deferredPrompt) return false;
        this._deferredPrompt.prompt();
        const { outcome } = await this._deferredPrompt.userChoice;
        this._deferredPrompt = null;
        if (outcome === 'accepted') {
            this._notifyAvailability(false);
        }
        return outcome === 'accepted';
    },

    dismiss: function () {
        this._notifyAvailability(false);
    }
};

window.pwaUpdate = {
    _dotNetHelper: null,
    _registration: null,
    _updateAvailable: false,

    _querySwVersion: function (sw) {
        return new Promise((resolve) => {
            if (!sw) { resolve(''); return; }
            const channel = new MessageChannel();
            const timeout = setTimeout(() => resolve(''), 1500);
            channel.port1.onmessage = (event) => {
                clearTimeout(timeout);
                resolve(event.data?.version ?? '');
            };
            sw.postMessage({ type: 'GET_VERSION' }, [channel.port2]);
        });
    },

    _setAvailability: async function (isAvailable) {
        if (this._updateAvailable === isAvailable) return;
        this._updateAvailable = isAvailable;
        if (!this._dotNetHelper) return;

        const currentVersion = window.APP_VERSION ?? '';
        let newVersion = '';
        if (isAvailable && this._registration?.waiting) {
            newVersion = await this._querySwVersion(this._registration.waiting);
        }
        this._dotNetHelper.invokeMethodAsync('SetUpdateAvailability', isAvailable, currentVersion, newVersion);
    },

    _syncAvailabilityFromRegistration: function (registration) {
        this._setAvailability(!!registration && !!registration.waiting);
    },

    init: function (dotNetHelper) {
        this._dotNetHelper = dotNetHelper;

        if (!('serviceWorker' in navigator)) return;

        navigator.serviceWorker.ready.then(registration => {
            this._registration = registration;
            this._syncAvailabilityFromRegistration(registration);

            // Already a SW waiting (e.g. user had the tab open when update arrived)
            if (registration.waiting) {
                return;
            }

            registration.addEventListener('updatefound', () => {
                const newWorker = registration.installing;
                if (!newWorker) return;

                newWorker.addEventListener('statechange', () => {
                    // installed + there is an active controller = update ready
                    if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                        this._syncAvailabilityFromRegistration(registration);
                    }
                });
            });

            // Proactively check for a new version
            registration.update().catch(() => { });

            // Keep checking periodically while the app is open.
            window.setInterval(() => {
                registration.update()
                    .then(() => this._syncAvailabilityFromRegistration(registration))
                    .catch(() => { });
            }, 5 * 60 * 1000);
        });
    },

    checkForUpdate: function () {
        const reg = this._registration;
        if (!reg) return;
        reg.update()
            .then(() => this._syncAvailabilityFromRegistration(reg))
            .catch(() => { });
    },

    getAppVersion: function () {
        return window.APP_VERSION ?? '';
    },

    applyUpdate: function () {
        const reg = this._registration;
        if (!reg || !reg.waiting) {
            window.location.reload();
            return;
        }
        // Fallback: reload after 3s in case controllerchange never fires
        const reloadTimeout = setTimeout(() => window.location.reload(), 3000);
        navigator.serviceWorker.addEventListener('controllerchange', () => {
            clearTimeout(reloadTimeout);
            window.location.reload();
        }, { once: true });
        reg.waiting.postMessage({ type: 'SKIP_WAITING' });
    }
};
