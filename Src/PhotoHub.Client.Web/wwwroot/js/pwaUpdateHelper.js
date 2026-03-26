window.pwaUpdate = {
    _dotNetHelper: null,
    _registration: null,
    _updateAvailable: false,

    _setAvailability: function (isAvailable) {
        if (this._updateAvailable === isAvailable) return;
        this._updateAvailable = isAvailable;
        if (this._dotNetHelper) {
            this._dotNetHelper.invokeMethodAsync('SetUpdateAvailability', isAvailable);
        }
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
