import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { SettingsStore } from '@iptvnator/services';
import { PlayerSettingsVideoPlayerRowComponent } from './player-settings-video-player-row.component';

describe('PlayerSettingsVideoPlayerRowComponent', () => {
    const original = window.matchMedia;

    function render(touch: boolean) {
        window.matchMedia = jest.fn(() => ({
            matches: touch,
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
        })) as unknown as typeof window.matchMedia;
        TestBed.configureTestingModule({
            imports: [
                PlayerSettingsVideoPlayerRowComponent,
                TranslateModule.forRoot(),
            ],
            providers: [
                provideRouter([]),
                {
                    provide: SettingsStore,
                    useValue: { player: signal('artplayer') },
                },
            ],
        });
        const fixture = TestBed.createComponent(
            PlayerSettingsVideoPlayerRowComponent
        );
        fixture.detectChanges();
        return fixture;
    }

    afterEach(() => {
        window.matchMedia = original;
    });

    it('links a touch screen to Settings › Playback with the current player', () => {
        const fixture = render(true);
        const navigated = jest.fn();
        fixture.componentInstance.navigated.subscribe(navigated);
        const row = fixture.nativeElement.querySelector(
            '[data-test-id="player-settings-video-player"]'
        ) as HTMLAnchorElement;

        expect(row.getAttribute('href')).toBe('/workspace/settings/playback');
        expect(row.textContent).toContain('SETTINGS.PLAYER_ARTPLAYER');
        row.click();
        expect(navigated).toHaveBeenCalledTimes(1);
    });

    it('renders nothing with a mouse', () => {
        expect(render(false).nativeElement.querySelector('a')).toBeNull();
    });
});
