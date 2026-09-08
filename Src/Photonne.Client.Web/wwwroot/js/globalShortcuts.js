// Atajos globales de la app (no de la rejilla): "/" va a buscar y "?" abre la
// ayuda de atajos. Inertes al escribir en campos, con un diálogo Mud abierto o
// con el visor de assets en pantalla (allí mandan sus propios atajos).
window.globalShortcuts = (() => {
    let handler = null;

    return {
        init(dotnetRef) {
            if (handler) document.removeEventListener('keydown', handler);
            handler = e => {
                if (e.ctrlKey || e.metaKey || e.altKey) return;
                if (e.target.closest?.('input, textarea, [contenteditable]')) return;
                if (document.querySelector('.mud-dialog-container, .asset-viewer')) return;
                if (e.key === '/') {
                    e.preventDefault();
                    dotnetRef.invokeMethodAsync('OnSlashShortcut');
                } else if (e.key === '?') {
                    e.preventDefault();
                    dotnetRef.invokeMethodAsync('OnHelpShortcut');
                }
            };
            document.addEventListener('keydown', handler);
        },
        dispose() {
            if (handler) {
                document.removeEventListener('keydown', handler);
                handler = null;
            }
        }
    };
})();
