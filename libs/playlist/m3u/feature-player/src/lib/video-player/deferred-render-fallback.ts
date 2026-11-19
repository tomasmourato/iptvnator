import { DestroyRef, Signal, inject, signal } from '@angular/core';

/**
 * Becomes true after `delayMs`, for `@defer (on idle; when due())`. A busy
 * device can go a long time without an idle period (an Android emulator
 * never fired `requestIdleCallback` at all), so an idle-only block would
 * stay on its placeholder. A `when` condition reuses runtime the app
 * already ships, where `on timer` adds Angular's timer trigger to the
 * initial bundle. Call it in an injection context.
 */
export function deferredRenderFallback(delayMs: number): Signal<boolean> {
    const due = signal(false);
    const timer = setTimeout(() => due.set(true), delayMs);
    inject(DestroyRef).onDestroy(() => clearTimeout(timer));
    return due.asReadonly();
}
