import {
    ChangeDetectionStrategy,
    Component,
    computed,
    inject,
    output,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SettingsStore } from '@iptvnator/services';
import { VideoPlayer } from '@iptvnator/shared/interfaces';
import {
    COARSE_POINTER_QUERY,
    injectMediaQuery,
} from '@iptvnator/ui/components/phone-layout';

/** The settings page's labels for each player (settings-options.ts). */
const PLAYER_LABEL_KEYS: Readonly<Record<VideoPlayer, string>> = {
    [VideoPlayer.Html5Player]: 'SETTINGS.PLAYER_HTML5',
    [VideoPlayer.VideoJs]: 'SETTINGS.PLAYER_VIDEOJS',
    [VideoPlayer.ArtPlayer]: 'SETTINGS.PLAYER_ARTPLAYER',
    [VideoPlayer.EmbeddedMpv]: 'SETTINGS.PLAYER_EMBEDDED_MPV',
    [VideoPlayer.MPV]: 'SETTINGS.PLAYER_MPV',
    [VideoPlayer.VLC]: 'SETTINGS.PLAYER_VLC',
};

/**
 * Top row of the player's settings sheet on a touch screen: the default video
 * player, a tap away from Settings › Playback. A phone user looks for it
 * where playback goes wrong, not in a settings section behind the drawer.
 * Renders nothing with a fine pointer.
 */
@Component({
    selector: 'app-player-settings-video-player-row',
    imports: [MatIconModule, RouterLink, TranslatePipe],
    template: `
        @if (isTouch()) {
            <a
                class="video-player-row"
                routerLink="/workspace/settings/playback"
                data-test-id="player-settings-video-player"
                (click)="leave()"
            >
                <mat-icon aria-hidden="true">smart_display</mat-icon>
                <span class="video-player-row__text">
                    <span>{{ 'SETTINGS.VIDEO_PLAYER_LABEL' | translate }}</span>
                    <span class="video-player-row__value">{{
                        playerLabelKey() | translate
                    }}</span>
                </span>
                <mat-icon aria-hidden="true">chevron_right</mat-icon>
            </a>
        }
    `,
    styles: `
        .video-player-row {
            display: flex;
            align-items: center;
            gap: 12px;
            min-height: 48px;
            margin-bottom: 12px;
            padding: 6px 12px;
            border-radius: 12px;
            background: rgba(255, 255, 255, 0.08);
            color: inherit;
            text-decoration: none;
        }
        .video-player-row__text {
            display: flex;
            flex: 1 1 auto;
            flex-direction: column;
            min-width: 0;
            font-weight: 500;
        }
        .video-player-row__value {
            opacity: 0.7;
            font-size: 0.84em;
        }
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayerSettingsVideoPlayerRowComponent {
    private readonly settingsStore = inject(SettingsStore);
    readonly isTouch = injectMediaQuery(COARSE_POINTER_QUERY);
    /** The row navigates away: the sheet closes. */
    readonly navigated = output<void>();

    readonly playerLabelKey = computed(
        () =>
            PLAYER_LABEL_KEYS[this.settingsStore.player()] ??
            PLAYER_LABEL_KEYS[VideoPlayer.Html5Player]
    );

    leave(): void {
        // Settings is not a fullscreen page: leave fullscreen with the player.
        if (document.fullscreenElement) {
            void document.exitFullscreen?.();
        }
        this.navigated.emit();
    }
}
