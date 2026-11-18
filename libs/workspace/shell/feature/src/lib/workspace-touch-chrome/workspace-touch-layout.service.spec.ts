import { TestBed } from '@angular/core/testing';
import { RuntimeCapabilitiesService } from '@iptvnator/services';
import { PHONE_LAYOUT_QUERY } from '@iptvnator/ui/components/phone-layout';
import {
    TOUCH_LANDSCAPE_QUERY,
    WorkspaceTouchLayoutService,
} from './workspace-touch-layout.service';

describe('WorkspaceTouchLayoutService', () => {
    const original = window.matchMedia;

    function create(
        shellMode: 'touch' | 'tv' | null,
        matching: string[]
    ): WorkspaceTouchLayoutService {
        window.matchMedia = jest.fn((query: string) => ({
            matches: matching.includes(query),
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
        })) as unknown as typeof window.matchMedia;
        TestBed.configureTestingModule({
            providers: [
                WorkspaceTouchLayoutService,
                {
                    provide: RuntimeCapabilitiesService,
                    useValue: {
                        shell: shellMode
                            ? { kind: 'android-tv-shell', mode: shellMode }
                            : null,
                    },
                },
            ],
        });
        return TestBed.inject(WorkspaceTouchLayoutService);
    }

    afterEach(() => {
        window.matchMedia = original;
    });

    it('shows the bottom bar on a touch-mode phone in portrait', () => {
        expect(create('touch', [PHONE_LAYOUT_QUERY]).mode()).toBe('portrait');
    });

    it('shows the compact layout on a touch-mode phone held sideways', () => {
        expect(create('touch', [TOUCH_LANDSCAPE_QUERY]).mode()).toBe(
            'landscape'
        );
    });

    it('leaves a touch-mode tablet alone', () => {
        expect(create('touch', []).mode()).toBeNull();
    });

    it('leaves TV mode, browsers and Electron alone at any size', () => {
        const everything = [PHONE_LAYOUT_QUERY, TOUCH_LANDSCAPE_QUERY];
        expect(create('tv', everything).mode()).toBeNull();
        TestBed.resetTestingModule();
        expect(create(null, everything).mode()).toBeNull();
    });
});
