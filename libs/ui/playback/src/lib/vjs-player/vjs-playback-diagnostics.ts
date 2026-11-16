import {
    InlinePlaybackPlayer,
    classifyNativePlaybackIssue,
    classifyVhsPlaybackIssue,
    createPlaybackSourceMetadata,
} from '@iptvnator/playback/util';
import {
    getVideoJsPlaybackCodecs,
    hasActiveVhsSourceHandler,
    type VideoJsPlayer,
    type VideoPlayerSource,
} from './vjs-player.types';

/** `MediaError.MEDIA_ERR_DECODE`; jsdom and some runtimes lack the constant. */
const MEDIA_ERR_DECODE = 3;

export function createVideoJsPlaybackDiagnostic(
    player: VideoJsPlayer,
    source: VideoPlayerSource | null | undefined,
    video: HTMLVideoElement
) {
    const error = typeof player.error === 'function' ? player.error() : null;
    const metadata = createPlaybackSourceMetadata({
        ...getVideoJsPlaybackCodecs(player),
        url: source?.src ?? video.currentSrc ?? '',
        mimeType: source?.type,
        player: InlinePlaybackPlayer.VideoJs,
    });
    // VHS also reports code 3 for "no available working or supported
    // playlists", so its own code 3 stays unknown. A decode failure is set on
    // the media element itself: that is the evidence for a decode error.
    const elementError = video.error;
    if (elementError?.code === MEDIA_ERR_DECODE) {
        return classifyNativePlaybackIssue(elementError, metadata);
    }
    return error && hasActiveVhsSourceHandler(player)
        ? classifyVhsPlaybackIssue(error, metadata)
        : classifyNativePlaybackIssue(error ?? video.error, metadata);
}
