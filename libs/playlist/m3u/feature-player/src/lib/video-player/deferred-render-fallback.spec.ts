import { DestroyRef, Injector, createEnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { deferredRenderFallback } from './deferred-render-fallback';

describe('deferredRenderFallback', () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    it('turns true once the delay has passed', () => {
        const due = TestBed.runInInjectionContext(() =>
            deferredRenderFallback(1000)
        );

        expect(due()).toBe(false);
        jest.advanceTimersByTime(999);
        expect(due()).toBe(false);
        jest.advanceTimersByTime(1);
        expect(due()).toBe(true);
    });

    it('drops its timer when the owner is destroyed', () => {
        const owner = createEnvironmentInjector(
            [],
            TestBed.inject(Injector) as never
        );
        const due = owner.runInContext(() => deferredRenderFallback(1000));

        expect(owner.get(DestroyRef)).toBeDefined();
        owner.destroy();
        expect(jest.getTimerCount()).toBe(0);
        expect(due()).toBe(false);
    });
});
