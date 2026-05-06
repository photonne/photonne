// Native-like back navigation helpers.
//
// Tracks per-history-entry "depth" so we can tell whether the current entry
// is the first one of the SPA session (depth 0). Back buttons in pages can
// then call history.back() and trust they'll return to the previous in-app
// entry — preserving scroll position, query params and any restored state
// the browser caches — instead of pushing a fresh entry to the canonical
// path (which loses context).
//
// The depth is stamped into history.state via replaceState. Push navigations
// get prevDepth + 1, replace navigations keep prevDepth, and popstate hits to
// entries we've already stamped reuse the existing value.
window.appNavHelpers = (function () {
    var lastSeenDepth = 0;
    var initialized = false;
    // Tracks whether the last history mutation was push or replace. Set by
    // patched pushState/replaceState below; consumed (and reset) by onNavigate.
    var lastOp = 'push';

    function getStateDepth() {
        var s = window.history.state;
        return s && typeof s.appDepth === 'number' ? s.appDepth : null;
    }

    function stamp(depth) {
        var current = window.history.state || {};
        var next = {};
        for (var k in current) { if (Object.prototype.hasOwnProperty.call(current, k)) next[k] = current[k]; }
        next.appDepth = depth;
        // Mark our internal call so the patched replaceState below doesn't
        // mis-classify us as the user's navigation.
        next.__appNavInternal = true;
        try { window.history.replaceState(next, ''); } catch (e) { /* ignore */ }
        delete next.__appNavInternal;
    }

    function patchHistory() {
        var origPush = window.history.pushState;
        var origReplace = window.history.replaceState;
        window.history.pushState = function (state) {
            lastOp = 'push';
            return origPush.apply(window.history, arguments);
        };
        window.history.replaceState = function (state) {
            // Skip flagging when stamp() calls us with our internal marker —
            // those are bookkeeping writes, not real user navigation.
            if (!(state && state.__appNavInternal)) {
                lastOp = 'replace';
            }
            return origReplace.apply(window.history, arguments);
        };
    }

    return {
        init: function () {
            if (initialized) return;
            initialized = true;
            patchHistory();
            var existing = getStateDepth();
            if (existing === null) {
                stamp(0);
                lastSeenDepth = 0;
            } else {
                lastSeenDepth = existing;
            }
            lastOp = 'push'; // reset after init's own stamp
        },

        // Called from C# after every LocationChanged. Stamps depth on the
        // new history entry (push) or syncs lastSeenDepth (popstate to an
        // already-stamped entry, or replace which keeps depth).
        onNavigate: function () {
            if (!initialized) this.init();
            var existing = getStateDepth();
            if (existing !== null) {
                lastSeenDepth = existing;
                lastOp = 'push';
                return existing;
            }
            var isReplace = lastOp === 'replace';
            lastOp = 'push';
            var newDepth = isReplace ? lastSeenDepth : lastSeenDepth + 1;
            stamp(newDepth);
            lastSeenDepth = newDepth;
            return newDepth;
        },

        canGoBack: function () {
            if (!initialized) this.init();
            var d = getStateDepth();
            return (d !== null ? d : lastSeenDepth) > 0;
        },

        back: function () {
            window.history.back();
        }
    };
})();
