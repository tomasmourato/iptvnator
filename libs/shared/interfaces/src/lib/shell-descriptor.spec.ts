import { VideoPlayer } from './settings.interface';
import { parseShellDescriptor } from './shell-descriptor';

describe('parseShellDescriptor', () => {
    it('reads a touch shell with its features and preferred player', () => {
        expect(
            parseShellDescriptor({
                kind: 'android',
                mode: 'touch',
                features: { externalPlayer: true, backupExport: false },
                preferredPlayer: 'html5',
            })
        ).toEqual({
            kind: 'android',
            mode: 'touch',
            features: { externalPlayer: true, backupExport: false },
            preferredPlayer: VideoPlayer.Html5Player,
        });
    });

    it('reads a TV shell without a preferred player', () => {
        expect(
            parseShellDescriptor({ kind: 'android', mode: 'tv' })
        ).toEqual({ kind: 'android', mode: 'tv', features: {} });
    });

    it.each([
        undefined,
        null,
        'android',
        [],
        {},
        { kind: '', mode: 'touch' },
        { kind: 'android', mode: 'desktop' },
        { kind: 7, mode: 'tv' },
    ])('treats a malformed descriptor (%p) as no shell', (value) => {
        expect(parseShellDescriptor(value)).toBeNull();
    });

    it('drops unknown features, non-boolean flags and non-web players', () => {
        expect(
            parseShellDescriptor({
                kind: 'android',
                mode: 'touch',
                features: { externalPlayer: 'yes', backupExport: true, other: true },
                preferredPlayer: VideoPlayer.VLC,
            })
        ).toEqual({
            kind: 'android',
            mode: 'touch',
            features: { backupExport: true },
        });
    });
});
