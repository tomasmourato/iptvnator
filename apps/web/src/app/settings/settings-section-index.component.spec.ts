import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { EpgRuntimeBridgeService } from '@iptvnator/epg/data-access';
import {
    RuntimeCapabilitiesService,
    ServerStorageStatusService,
    SettingsStore,
} from '@iptvnator/services';
import { SettingsSectionIndexComponent } from './settings-section-index.component';

describe('SettingsSectionIndexComponent', () => {
    const player = signal('html5');

    function render() {
        TestBed.configureTestingModule({
            imports: [SettingsSectionIndexComponent, TranslateModule.forRoot()],
            providers: [
                provideRouter([]),
                {
                    provide: RuntimeCapabilitiesService,
                    useValue: {
                        supportsEpg: false,
                        supportsProviderEpg: true,
                        supportsRemoteControl: false,
                    },
                },
                {
                    provide: EpgRuntimeBridgeService,
                    useValue: {
                        supportsImport: false,
                        supportsDataManagement: false,
                    },
                },
                {
                    provide: ServerStorageStatusService,
                    useValue: { isActive: () => false },
                },
                { provide: SettingsStore, useValue: { player } },
            ],
        });
        const fixture = TestBed.createComponent(SettingsSectionIndexComponent);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    it('lists every section this runtime offers, in navigation order, without Remote', () => {
        const ids = [
            ...render().querySelectorAll<HTMLElement>('.section-index__row'),
        ].map((row) => row.dataset['testId']);

        expect(ids).toEqual([
            'settings-index-general',
            'settings-index-appearance',
            'settings-index-playback',
            'settings-index-epg',
            'settings-index-dashboard',
            'settings-index-tmdb',
            'settings-index-parental',
            'settings-index-server',
            'settings-index-backup',
            'settings-index-about',
        ]);
    });

    it('opens a section as its own page', () => {
        const link = render().querySelector(
            '[data-test-id="settings-index-playback"]'
        );
        expect(link?.getAttribute('href')).toBe('/workspace/settings/playback');
    });

    it('states the default video player on the Playback row', () => {
        player.set('videojs');
        const value = render().querySelector(
            '[data-test-id="settings-index-player"]'
        );
        expect(value?.textContent).toContain('SETTINGS.PLAYER_VIDEOJS');
    });
});
