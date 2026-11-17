import { DestroyRef, inject, signal, type Signal } from '@angular/core';

/** The phone breakpoint (UI guidelines, "Phone Layout"). */
export const PHONE_LAYOUT_QUERY = '(max-width: 640px)';

/** A touch screen, at any width (phones in landscape too). */
export const COARSE_POINTER_QUERY = '(pointer: coarse)';

/**
 * Whether a media query matches, updated as it changes. For templates that
 * must not even create a component (and its stylesheet) where it does not
 * apply; layout alone belongs in the stylesheet's media query. Call it in an
 * injection context. False where `matchMedia` is missing (JSDOM).
 */
export function injectMediaQuery(query: string): Signal<boolean> {
    const media =
        typeof window !== 'undefined' && typeof window.matchMedia === 'function'
            ? window.matchMedia(query)
            : null;
    const matches = signal(media?.matches ?? false);
    if (media) {
        const onChange = (event: MediaQueryListEvent) =>
            matches.set(event.matches);
        media.addEventListener('change', onChange);
        inject(DestroyRef).onDestroy(() =>
            media.removeEventListener('change', onChange)
        );
    }
    return matches.asReadonly();
}

/** Whether the viewport is at the phone breakpoint. */
export function injectPhoneLayout(): Signal<boolean> {
    return injectMediaQuery(PHONE_LAYOUT_QUERY);
}
