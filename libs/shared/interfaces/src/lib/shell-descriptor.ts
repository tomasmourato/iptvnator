import { VideoPlayer } from './settings.interface';

/** How a native shell presents the app: remote-driven TV or touch screen. */
export type ShellMode = 'tv' | 'touch';

/**
 * Optional features a native shell implements for the app. A missing flag
 * means the shell does not provide the feature.
 */
export interface ShellFeatures {
    /** Streams can be handed to another installed player app. */
    readonly externalPlayer?: boolean;
    /** Backups can be saved to a user-chosen file. */
    readonly backupExport?: boolean;
}

/**
 * Capability descriptor published by a native shell that hosts the web app
 * (the Android tv-shell) as `window.__IPTVNATOR_CONFIG__.SHELL`. Runtime
 * behavior reads it through `RuntimeCapabilitiesService`, never through
 * CSS class markers.
 */
export interface ShellDescriptor {
    readonly kind: string;
    readonly mode: ShellMode;
    readonly features: ShellFeatures;
    /** Built-in player to use until the user picks one. */
    readonly preferredPlayer?: VideoPlayer;
}

const FEATURE_KEYS: ReadonlyArray<keyof ShellFeatures> = [
    'externalPlayer',
    'backupExport',
];

/** Players a shell may prefer: the built-in web engines only. */
const PREFERABLE_PLAYERS = new Set<string>([
    VideoPlayer.VideoJs,
    VideoPlayer.Html5Player,
    VideoPlayer.ArtPlayer,
]);

/** Validates an untrusted descriptor; anything malformed reads as no shell. */
export function parseShellDescriptor(value: unknown): ShellDescriptor | null {
    if (!isRecord(value)) return null;
    const { kind, mode, features, preferredPlayer } = value;
    if (typeof kind !== 'string' || !kind) return null;
    if (mode !== 'tv' && mode !== 'touch') return null;
    const flags: { -readonly [K in keyof ShellFeatures]: boolean } = {};
    if (isRecord(features)) {
        for (const key of FEATURE_KEYS) {
            if (typeof features[key] === 'boolean') flags[key] = features[key];
        }
    }
    return {
        kind,
        mode,
        features: flags,
        ...(typeof preferredPlayer === 'string' &&
        PREFERABLE_PLAYERS.has(preferredPlayer)
            ? { preferredPlayer: preferredPlayer as VideoPlayer }
            : {}),
    };
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
