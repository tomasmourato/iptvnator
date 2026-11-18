import {
    computed,
    Injectable,
    inject,
    signal,
    type Signal,
} from '@angular/core';
import { RuntimeCapabilitiesService } from '@iptvnator/services';
// File-level entry: the @iptvnator/ui/components barrel is in the startup bundle.
import {
    injectMediaQuery,
    injectPhoneLayout,
} from '@iptvnator/ui/components/phone-layout';
import { injectSoftKeyboardOpen } from './soft-keyboard';

/** A phone held sideways: short, touch, wider than tall. */
export const TOUCH_LANDSCAPE_QUERY =
    '(pointer: coarse) and (max-height: 500px) and (orientation: landscape)';

export type TouchChromeMode = 'portrait' | 'landscape';

/**
 * Which touch chrome the workspace shell shows (fork-only, tv-shell): the
 * bottom bar with the speed-dial Play on a phone in portrait, the compact
 * landscape layout on a phone held sideways, nothing elsewhere. Only the
 * tv-shell's touch mode turns it on (its shell capability descriptor), so
 * browsers, Electron, TV and tablets keep the regular shell.
 */
@Injectable()
export class WorkspaceTouchLayoutService {
    private readonly touchShell =
        inject(RuntimeCapabilitiesService).shell?.mode === 'touch';
    private readonly phone = this.touchShell
        ? injectPhoneLayout()
        : signal(false);
    private readonly landscape = this.touchShell
        ? injectMediaQuery(TOUCH_LANDSCAPE_QUERY)
        : signal(false);

    readonly mode = computed<TouchChromeMode | null>(() => {
        if (!this.touchShell) return null;
        if (this.phone()) return 'portrait';
        return this.landscape() ? 'landscape' : null;
    });

    /** The bottom bar steps aside for the soft keyboard. */
    readonly keyboardOpen: Signal<boolean> = this.touchShell
        ? injectSoftKeyboardOpen()
        : signal(false);
}
