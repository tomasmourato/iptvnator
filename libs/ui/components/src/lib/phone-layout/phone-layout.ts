import { DestroyRef, inject, signal, type Signal } from '@angular/core';

/** The phone breakpoint (UI guidelines, "Phone Layout"). */
export const PHONE_LAYOUT_QUERY = '(max-width: 640px)';

/**
 * Whether the viewport is at the phone breakpoint, updated as it changes.
 * For templates that must not even create a phone-only component (and its
 * stylesheet) on wider screens; layout alone belongs in the media query.
 * Call it in an injection context. False where `matchMedia` is missing
 * (JSDOM).
 */
export function injectPhoneLayout(): Signal<boolean> {
    const media =
        typeof window !== 'undefined' && typeof window.matchMedia === 'function'
            ? window.matchMedia(PHONE_LAYOUT_QUERY)
            : null;
    const isPhone = signal(media?.matches ?? false);
    if (media) {
        const onChange = (event: MediaQueryListEvent) =>
            isPhone.set(event.matches);
        media.addEventListener('change', onChange);
        inject(DestroyRef).onDestroy(() =>
            media.removeEventListener('change', onChange)
        );
    }
    return isPhone.asReadonly();
}
