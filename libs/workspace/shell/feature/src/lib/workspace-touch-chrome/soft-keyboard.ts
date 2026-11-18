import { DestroyRef, inject, signal, type Signal } from '@angular/core';

/** How far the visual viewport has to shrink before it counts as a keyboard. */
const KEYBOARD_MIN_PX = 150;

function editableFocused(doc: Document): boolean {
    const active = doc.activeElement as HTMLElement | null;
    return (
        !!active &&
        (active.isContentEditable ||
            /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName))
    );
}

/**
 * Whether the soft keyboard is open: a field has focus and the visual
 * viewport is well below its tallest height in this orientation. The tv-shell
 * resizes the page for the keyboard (IME inset, adjustResize) and a browser
 * overlays it; both shrink the visual viewport. The focus check keeps a
 * pinch-zoom from counting. Call it in an injection context.
 */
export function injectSoftKeyboardOpen(): Signal<boolean> {
    const doc = document;
    const viewport =
        typeof window !== 'undefined' ? window.visualViewport : null;
    const open = signal(false);
    if (!viewport) return open.asReadonly();

    let width = viewport.width;
    let tallest = viewport.height;
    const update = () => {
        // A rotation changes the width: the tallest height starts over.
        if (Math.abs(viewport.width - width) > 1) {
            width = viewport.width;
            tallest = viewport.height;
        }
        tallest = Math.max(tallest, viewport.height);
        open.set(
            tallest - viewport.height > KEYBOARD_MIN_PX && editableFocused(doc)
        );
    };
    viewport.addEventListener('resize', update);
    doc.addEventListener('focusin', update);
    doc.addEventListener('focusout', update);
    inject(DestroyRef).onDestroy(() => {
        viewport.removeEventListener('resize', update);
        doc.removeEventListener('focusin', update);
        doc.removeEventListener('focusout', update);
    });
    return open.asReadonly();
}
