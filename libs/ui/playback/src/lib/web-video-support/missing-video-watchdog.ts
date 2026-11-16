import {
    type InlinePlaybackPlayer,
    type PlaybackDiagnostic,
    PlaybackDiagnosticCode,
    PlaybackDiagnosticSource,
    createPlaybackDiagnostic,
    createPlaybackSourceMetadata,
} from '@iptvnator/playback/util';

/** How long a source may play with sound but no decoded picture before it is reported. */
export const MISSING_VIDEO_GRACE_MS = 8000;

/** Containers that legitimately carry no video track. */
const AUDIO_CONTAINERS = new Set([
    'aac',
    'flac',
    'm4a',
    'mp3',
    'oga',
    'ogg',
    'opus',
    'wav',
]);

export interface MissingVideoWatchdogConfig {
    readonly player: InlinePlaybackPlayer;
    readonly getSourceUrl: () => string | undefined;
    readonly emitPlaybackIssue: (issue: PlaybackDiagnostic) => void;
    readonly graceMs?: number;
}

/**
 * Reports a source that plays (the clock advances, audio decodes) while the
 * media element never decodes a video frame. A transmuxer that does not know
 * the video codec drops the track silently, e.g. Video.js/mux.js with HEVC in
 * MPEG-TS: no engine error is raised and the loading indicator never ends.
 */
export class MissingVideoWatchdog {
    private video: HTMLVideoElement | null = null;
    private timer: ReturnType<typeof setTimeout> | undefined;

    private readonly arm = (): void => {
        if (this.timer !== undefined) return;
        this.timer = setTimeout(
            this.check,
            this.config.graceMs ?? MISSING_VIDEO_GRACE_MS
        );
    };

    private readonly disarm = (): void => {
        if (this.timer === undefined) return;
        clearTimeout(this.timer);
        this.timer = undefined;
    };

    private readonly check = (): void => {
        this.timer = undefined;
        const video = this.video;
        if (!video || video.paused || video.ended) return;
        if (video.videoWidth > 0 || decodedVideoFrames(video) > 0) return;
        const metadata = createPlaybackSourceMetadata({
            url: this.config.getSourceUrl() ?? video.currentSrc ?? '',
            player: this.config.player,
        });
        if (AUDIO_CONTAINERS.has(metadata.container)) return;
        this.config.emitPlaybackIssue(
            createPlaybackDiagnostic({
                code: PlaybackDiagnosticCode.UnsupportedCodec,
                source: PlaybackDiagnosticSource.Native,
                metadata,
            })
        );
    };

    private readonly listeners: ReadonlyArray<
        readonly [event: string, listener: EventListener]
    > = [
        ['playing', this.arm],
        ['loadstart', this.disarm],
        ['emptied', this.disarm],
        ['pause', this.disarm],
    ];

    constructor(private readonly config: MissingVideoWatchdogConfig) {}

    bind(video: HTMLVideoElement): void {
        if (this.video === video) return;
        this.destroy();
        this.video = video;
        for (const [event, listener] of this.listeners) {
            video.addEventListener(event, listener);
        }
    }

    destroy(): void {
        this.disarm();
        if (!this.video) return;
        for (const [event, listener] of this.listeners) {
            this.video.removeEventListener(event, listener);
        }
        this.video = null;
    }
}

/** Frames decoded so far; unknown counts as decoded so the watchdog stays quiet. */
function decodedVideoFrames(video: HTMLVideoElement): number {
    if (typeof video.getVideoPlaybackQuality === 'function') {
        return video.getVideoPlaybackQuality().totalVideoFrames;
    }
    const legacy = (video as { webkitDecodedFrameCount?: number })
        .webkitDecodedFrameCount;
    return typeof legacy === 'number' ? legacy : 1;
}
