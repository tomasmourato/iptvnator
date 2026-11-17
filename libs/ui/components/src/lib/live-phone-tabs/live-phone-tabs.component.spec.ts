import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { LivePhoneTabsComponent } from './live-phone-tabs.component';

describe('LivePhoneTabsComponent', () => {
    const original = window.matchMedia;

    function create(phone: boolean) {
        window.matchMedia = jest.fn(() => ({
            matches: phone,
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
        })) as unknown as typeof window.matchMedia;
        TestBed.configureTestingModule({
            imports: [LivePhoneTabsComponent, TranslateModule.forRoot()],
        });
        const fixture = TestBed.createComponent(LivePhoneTabsComponent);
        fixture.detectChanges();
        return fixture;
    }

    afterEach(() => {
        window.matchMedia = original;
    });

    it('starts on the channel list and exposes the pane on its host', () => {
        const fixture = create(true);
        const host: HTMLElement = fixture.nativeElement;

        expect(host.dataset['pane']).toBe('channels');
        expect(
            host
                .querySelector('[data-test-id="live-phone-tab-channels"]')
                ?.getAttribute('aria-selected')
        ).toBe('true');
    });

    it('switches to the guide and asks the layout to expand it', () => {
        const fixture = create(true);
        const guideSelected = jest.fn();
        fixture.componentInstance.guideSelected.subscribe(guideSelected);
        const host: HTMLElement = fixture.nativeElement;

        host.querySelector<HTMLButtonElement>(
            '[data-test-id="live-phone-tab-guide"]'
        )?.click();
        fixture.detectChanges();

        expect(host.dataset['pane']).toBe('guide');
        expect(guideSelected).toHaveBeenCalledTimes(1);
    });

    it('renders nothing above the phone breakpoint', () => {
        const fixture = create(false);
        expect(fixture.nativeElement.querySelector('[role="tab"]')).toBeNull();
    });
});
