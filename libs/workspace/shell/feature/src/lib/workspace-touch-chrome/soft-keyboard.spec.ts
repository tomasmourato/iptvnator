import { TestBed } from '@angular/core/testing';
import { injectSoftKeyboardOpen } from './soft-keyboard';

class FakeViewport extends EventTarget {
    width = 411;
    height = 840;

    resizeTo(width: number, height: number): void {
        this.width = width;
        this.height = height;
        this.dispatchEvent(new Event('resize'));
    }
}

describe('injectSoftKeyboardOpen', () => {
    let viewport: FakeViewport;
    let input: HTMLInputElement;

    beforeEach(() => {
        viewport = new FakeViewport();
        Object.defineProperty(window, 'visualViewport', {
            value: viewport,
            configurable: true,
        });
        input = document.createElement('input');
        document.body.append(input);
    });

    afterEach(() => {
        input.remove();
        Object.defineProperty(window, 'visualViewport', {
            value: undefined,
            configurable: true,
        });
    });

    const open = () =>
        TestBed.runInInjectionContext(() => injectSoftKeyboardOpen());

    it('opens when a field has focus and the viewport shrinks by a keyboard', () => {
        const keyboard = open();
        input.focus();
        viewport.resizeTo(411, 527);
        expect(keyboard()).toBe(true);

        viewport.resizeTo(411, 840);
        expect(keyboard()).toBe(false);
    });

    it('ignores a shrink without a focused field (pinch-zoom)', () => {
        const keyboard = open();
        viewport.resizeTo(411, 400);
        expect(keyboard()).toBe(false);
    });

    it('starts over after a rotation', () => {
        const keyboard = open();
        input.focus();
        // Landscape is shorter than portrait, but no keyboard is open.
        viewport.resizeTo(864, 359);
        expect(keyboard()).toBe(false);
    });
});
