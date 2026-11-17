/*
 * IPTVnator TV shell — system colour scheme (touch mode only).
 *
 * The system WebView reports `prefers-color-scheme: light` whatever Android's
 * dark theme is (checked on WebView 157 with a dark activity theme and
 * algorithmic darkening allowed), so the app's System theme never turned dark
 * on a phone. The app reads that preference through window.matchMedia only
 * (its styles switch on a body class), so the shell answers those queries.
 *
 * Injected natively before any page script (AndroidX WebKit document-start
 * script, the shell's own origin only, touch mode only). The shell fills in
 * the placeholder when it registers the script and calls
 * window.__tvShellColorScheme(dark) when Android's dark theme changes while a
 * page is open. Every other query goes to the real matchMedia.
 */
(() => {
    'use strict';
    let dark = __TVSHELL_DARK__;
    const SCHEME = /^\s*\(\s*prefers-color-scheme\s*:\s*(dark|light)\s*\)\s*$/i;
    const nativeMatchMedia = window.matchMedia.bind(window);
    const lists = new Set();

    class SchemeQueryList extends EventTarget {
        constructor(media, wantsDark) {
            super();
            this.media = media;
            this.onchange = null;
            this.wantsDark = wantsDark;
            this.legacy = [];
        }

        get matches() {
            return dark === this.wantsDark;
        }

        addListener(listener) {
            if (typeof listener === 'function') this.legacy.push(listener);
        }

        removeListener(listener) {
            const at = this.legacy.indexOf(listener);
            if (at >= 0) this.legacy.splice(at, 1);
        }

        notify() {
            const init = { matches: this.matches, media: this.media };
            const event =
                typeof MediaQueryListEvent === 'function'
                    ? new MediaQueryListEvent('change', init)
                    : Object.assign(new Event('change'), init);
            this.dispatchEvent(event);
            if (typeof this.onchange === 'function') this.onchange.call(this, event);
            for (const listener of this.legacy.slice()) listener.call(this, event);
        }
    }

    window.matchMedia = (query) => {
        const scheme = SCHEME.exec(String(query));
        if (!scheme) return nativeMatchMedia(query);
        const list = new SchemeQueryList(String(query), scheme[1].toLowerCase() === 'dark');
        lists.add(new WeakRef(list));
        return list;
    };

    window.__tvShellColorScheme = (nextDark) => {
        if (typeof nextDark !== 'boolean' || nextDark === dark) return;
        dark = nextDark;
        for (const ref of lists) {
            const list = ref.deref();
            if (list) list.notify();
            else lists.delete(ref);
        }
    };
})();
