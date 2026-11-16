import {
    InlinePlaybackPlayer,
    type PlaybackDiagnostic,
} from '@iptvnator/playback/util';
import { releaseVideoPictureInPicture } from '../player-controls/web-video-picture-in-picture-lifecycle';
import { MissingVideoWatchdog } from '../web-video-support/missing-video-watchdog';

export interface VjsVideoElementSessionConfig {
    releasePictureInPicture?: boolean;
    /** `null` clears the current issue. */
    emitPlaybackIssue: (issue: PlaybackDiagnostic | null) => void;
    emitPlaybackEnded: () => void;
    emitPlaybackStarted?: () => void;
    /** Set: report a source that plays without ever decoding video. */
    getSourceUrl?: () => string | undefined;
}

export class VjsVideoElementSession {
    private currentVideo: HTMLVideoElement | null = null;
    private destroyed = false;
    private readonly missingVideo: MissingVideoWatchdog | null;

    private readonly handlePlaying = (): void => {
        this.clearPlaybackIssue();
        this.config.emitPlaybackStarted?.();
    };

    private readonly clearPlaybackIssue = () => {
        this.config.emitPlaybackIssue(null);
    };

    private readonly emitPlaybackEnded = () => {
        this.config.emitPlaybackEnded();
    };

    constructor(private readonly config: VjsVideoElementSessionConfig) {
        const { getSourceUrl } = config;
        this.missingVideo = getSourceUrl
            ? new MissingVideoWatchdog({
                  player: InlinePlaybackPlayer.VideoJs,
                  getSourceUrl,
                  emitPlaybackIssue: (issue) => config.emitPlaybackIssue(issue),
              })
            : null;
    }

    bind(video: HTMLVideoElement): void {
        if (this.destroyed || this.currentVideo === video) {
            return;
        }

        this.detach();
        this.currentVideo = video;
        video.addEventListener('loadeddata', this.clearPlaybackIssue);
        video.addEventListener('playing', this.handlePlaying);
        video.addEventListener('ended', this.emitPlaybackEnded);
        this.missingVideo?.bind(video);
    }

    video(): HTMLVideoElement | null {
        return this.currentVideo;
    }

    destroy(): void {
        if (this.destroyed) {
            return;
        }

        this.detach();
        this.destroyed = true;
    }

    private detach(): void {
        if (!this.currentVideo) {
            return;
        }

        if (this.config.releasePictureInPicture) {
            releaseVideoPictureInPicture(this.currentVideo);
        }
        this.currentVideo.removeEventListener(
            'loadeddata',
            this.clearPlaybackIssue
        );
        this.currentVideo.removeEventListener('playing', this.handlePlaying);
        this.currentVideo.removeEventListener('ended', this.emitPlaybackEnded);
        this.missingVideo?.destroy();
        this.currentVideo = null;
    }
}
