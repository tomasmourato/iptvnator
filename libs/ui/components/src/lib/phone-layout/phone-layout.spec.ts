import { TestBed } from '@angular/core/testing';
import { injectPhoneLayout, PHONE_LAYOUT_QUERY } from './phone-layout';

describe('injectPhoneLayout', () => {
    const original = window.matchMedia;
    let listener: ((event: MediaQueryListEvent) => void) | null = null;
    const removeEventListener = jest.fn();

    beforeEach(() => {
        listener = null;
        window.matchMedia = jest.fn((query: string) => ({
            matches: query === PHONE_LAYOUT_QUERY,
            addEventListener: (_: string, fn: typeof listener) => {
                listener = fn;
            },
            removeEventListener,
        })) as unknown as typeof window.matchMedia;
    });

    afterEach(() => {
        window.matchMedia = original;
    });

    it('follows the phone breakpoint and stops listening when destroyed', () => {
        const isPhone = TestBed.runInInjectionContext(() =>
            injectPhoneLayout()
        );
        expect(isPhone()).toBe(true);

        listener?.({ matches: false } as MediaQueryListEvent);
        expect(isPhone()).toBe(false);

        TestBed.resetTestingModule();
        expect(removeEventListener).toHaveBeenCalled();
    });

    it('reports no phone where matchMedia is missing', () => {
        window.matchMedia = undefined as unknown as typeof window.matchMedia;
        const isPhone = TestBed.runInInjectionContext(() =>
            injectPhoneLayout()
        );
        expect(isPhone()).toBe(false);
    });
});
