window.deviceLayout = {
    _ref: null,
    _handler: null,
    _timer: null,
    initialize: function (dotNetRef) {
        this._ref = dotNetRef;
        this._handler = function () {
            clearTimeout(window.deviceLayout._timer);
            window.deviceLayout._timer = setTimeout(function () {
                if (window.deviceLayout._ref) {
                    window.deviceLayout._ref.invokeMethodAsync('OnResize', window.innerWidth);
                }
            }, 150);
        };
        window.addEventListener('resize', this._handler);
        return window.innerWidth;
    },
    dispose: function () {
        if (this._handler) {
            window.removeEventListener('resize', this._handler);
            this._handler = null;
        }
        this._ref = null;
    }
};
