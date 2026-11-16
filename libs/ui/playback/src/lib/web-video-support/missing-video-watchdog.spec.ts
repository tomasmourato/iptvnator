import { jest } from '@jest/globals';
import {
    InlinePlaybackPlayer,
    type PlaybackDiagnostic,
} from '@iptvnator/playback/util';
import { MissingVideoWatchdog } from './missing-video-watchdog';

interface FakeVideoState {
    paused: boolean;
    ended: boolean;
    videoWidth: number;
    frames: number | null;
}

function createVideo(state: FakeVideoState): HTMLVideoElement {
    const video = document.createElement('video');
    Object.defineProperties(video, {
        paused: { get: () => state.paused },
        ended: { get: () => state.ended },
        videoWidth: { get: () => state.videoWidth },
    });
    if (state.frames !== null) {
        Object.defineProperty(video, 'getVideoPlaybackQuality', {
            value: () => ({ totalVideoFrames: state.frames }),
        });
    }
    return video;
}

describe('MissingVideoWatchdog', () => {
    let issues: PlaybackDiagnostic[];
    let state: FakeVideoState;
    let url: string;

    function start(): HTMLVideoElement {
        const video = createVideo(state);
        const watchdog = new MissingVideoWatchdog({
            player: InlinePlaybackPlayer.VideoJs,
            getSourceUrl: () => url,
            emitPlaybackIssue: (issue) => issues.push(issue),
            graceMs: 1000,
        });
        watchdog.bind(video);
        video.dispatchEvent(new Event('playing'));
        return video;
    }

    beforeEach(() => {
        jest.useFakeTimers();
        issues = [];
        state = { paused: false, ended: false, videoWidth: 0, frames: 0 };
        url = 'https://example.com/live/channel.m3u8';
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    it('reports a playing source that never decodes a video frame', () => {
        start();

        jest.advanceTimersByTime(1000);

        expect(issues).toHaveLength(1);
        expect(issues[0]).toEqual(
            expect.objectContaining({
                code: 'unsupported-codec',
                source: 'native',
                player: InlinePlaybackPlayer.VideoJs,
                sourceUrl: 'https://example.com/live/channel.m3u8',
            })
        );
    });

    it('stays quiet once video frames decode', () => {
        state.frames = 25;
        state.videoWidth = 1280;
        start();

        jest.advanceTimersByTime(1000);

        expect(issues).toEqual([]);
    });

    it('stays quiet for audio containers', () => {
        url = 'https://example.com/radio/stream.mp3';
        start();

        jest.advanceTimersByTime(1000);

        expect(issues).toEqual([]);
    });

    it('stays quiet when the frame count is unavailable', () => {
        state.frames = null;
        start();

        jest.advanceTimersByTime(1000);

        expect(issues).toEqual([]);
    });

    it('does not report a source that was paused or replaced before the grace period', () => {
        const video = start();
        video.dispatchEvent(new Event('pause'));
        jest.advanceTimersByTime(1000);

        video.dispatchEvent(new Event('playing'));
        video.dispatchEvent(new Event('loadstart'));
        jest.advanceTimersByTime(1000);

        expect(issues).toEqual([]);
    });

    it('stops listening after destroy', () => {
        const video = createVideo(state);
        const watchdog = new MissingVideoWatchdog({
            player: InlinePlaybackPlayer.Html5,
            getSourceUrl: () => url,
            emitPlaybackIssue: (issue) => issues.push(issue),
            graceMs: 1000,
        });
        watchdog.bind(video);
        video.dispatchEvent(new Event('playing'));
        watchdog.destroy();
        video.dispatchEvent(new Event('playing'));

        jest.advanceTimersByTime(1000);

        expect(issues).toEqual([]);
    });
});
