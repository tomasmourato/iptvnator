import { VjsVideoElementSession } from './vjs-video-element-session';

describe('VjsVideoElementSession', () => {
    it('binds native playback events once and forwards them', () => {
        const emitPlaybackIssue = jest.fn();
        const emitPlaybackEnded = jest.fn();
        const video = document.createElement('video');
        const addEventListener = jest.spyOn(video, 'addEventListener');
        const session = new VjsVideoElementSession({
            emitPlaybackIssue,
            emitPlaybackEnded,
        });

        session.bind(video);
        session.bind(video);
        video.dispatchEvent(new Event('loadeddata'));
        video.dispatchEvent(new Event('playing'));
        video.dispatchEvent(new Event('ended'));

        expect(addEventListener).toHaveBeenCalledTimes(3);
        expect(emitPlaybackIssue).toHaveBeenCalledTimes(2);
        expect(emitPlaybackIssue).toHaveBeenCalledWith(null);
        expect(emitPlaybackEnded).toHaveBeenCalledTimes(1);
        expect(session.video()).toBe(video);
    });

    it('detaches the old Tech video before binding its replacement', () => {
        const emitPlaybackIssue = jest.fn();
        const emitPlaybackEnded = jest.fn();
        const firstVideo = document.createElement('video');
        const secondVideo = document.createElement('video');
        const firstRemoveEventListener = jest.spyOn(
            firstVideo,
            'removeEventListener'
        );
        const session = new VjsVideoElementSession({
            emitPlaybackIssue,
            emitPlaybackEnded,
        });

        session.bind(firstVideo);
        session.bind(secondVideo);
        firstVideo.dispatchEvent(new Event('ended'));
        secondVideo.dispatchEvent(new Event('ended'));

        expect(firstRemoveEventListener).toHaveBeenCalledTimes(3);
        expect(emitPlaybackEnded).toHaveBeenCalledTimes(1);
        expect(session.video()).toBe(secondVideo);
    });

    it('destroys idempotently and ignores events from the detached video', () => {
        const emitPlaybackIssue = jest.fn();
        const emitPlaybackEnded = jest.fn();
        const video = document.createElement('video');
        const removeEventListener = jest.spyOn(video, 'removeEventListener');
        const session = new VjsVideoElementSession({
            emitPlaybackIssue,
            emitPlaybackEnded,
        });

        session.bind(video);
        session.destroy();
        session.destroy();
        video.dispatchEvent(new Event('loadeddata'));
        video.dispatchEvent(new Event('ended'));

        expect(removeEventListener).toHaveBeenCalledTimes(3);
        expect(emitPlaybackIssue).not.toHaveBeenCalled();
        expect(emitPlaybackEnded).not.toHaveBeenCalled();
        expect(session.video()).toBeNull();
    });

    it('reports a source that plays without decoding video when given its URL', () => {
        jest.useFakeTimers();
        const emitPlaybackIssue = jest.fn();
        const video = document.createElement('video');
        Object.defineProperties(video, {
            paused: { get: () => false },
            getVideoPlaybackQuality: {
                value: () => ({ totalVideoFrames: 0 }),
            },
        });
        const session = new VjsVideoElementSession({
            emitPlaybackIssue,
            emitPlaybackEnded: jest.fn(),
            getSourceUrl: () => 'https://example.com/live/channel.m3u8',
        });

        session.bind(video);
        video.dispatchEvent(new Event('playing'));
        jest.advanceTimersByTime(8000);
        session.destroy();
        jest.useRealTimers();

        expect(emitPlaybackIssue).toHaveBeenLastCalledWith(
            expect.objectContaining({
                code: 'unsupported-codec',
                sourceUrl: 'https://example.com/live/channel.m3u8',
            })
        );
    });
});
