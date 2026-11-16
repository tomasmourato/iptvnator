/**
 * Synthetic oversized Xtream catalogues for mock-provider.mjs (--large-catalog [MiB]): real panels
 * answer get_vod_streams or get_series with 80 MB and more, which a device must hold while it
 * relays them. Items are generated while the answer is written, never held in full, with
 * non-ASCII names so the UTF-8 path is exercised. Everything here is synthetic.
 */
const MIB = 1024 * 1024;
const PLOT = 'A synthetic plot written for the large-catalogue check. '.repeat(3).trim();

export const LARGE_VOD_ID = 1_000_000;
export const LARGE_SERIES_ID = 2_000_000;

function vodItem(i) {
    return {
        num: i + 1,
        name: `Filme sintético ${i + 1} – Ação`,
        stream_type: 'movie',
        stream_id: LARGE_VOD_ID + i,
        stream_icon: '',
        rating: '7.5',
        rating_5based: 3.8,
        added: '1700000000',
        category_id: i % 2 ? '202' : '201',
        container_extension: 'mp4',
        custom_sid: '',
        direct_source: '',
        plot: PLOT,
    };
}

function seriesItem(i) {
    return {
        num: i + 1,
        name: `Série sintética ${i + 1} – Ação`,
        series_id: LARGE_SERIES_ID + i,
        cover: '',
        plot: PLOT,
        cast: '',
        director: '',
        genre: 'Drama',
        releaseDate: '2020-01-01',
        last_modified: '1700000000',
        rating: '8',
        rating_5based: 4,
        backdrop_path: [],
        youtube_trailer: '',
        episode_run_time: '45',
        category_id: '301',
    };
}

/** Bytes of one answer with {@code items} entries, without writing it. */
function sizeOf(item, items) {
    let n = 2 + Math.max(0, items - 1);
    for (let i = 0; i < items; i++) n += Buffer.byteLength(JSON.stringify(item(i)));
    return n;
}

/** Number of entries whose answer first reaches {@code mib} MiB (searched once per kind). */
const countCache = new Map();
function countFor(kind, mib) {
    const key = `${kind}:${mib}`;
    if (!countCache.has(key)) {
        const item = kind === 'vod' ? vodItem : seriesItem;
        const perItem = sizeOf(item, 1000) / 1000;
        let items = Math.ceil((mib * MIB) / perItem);
        while (sizeOf(item, items) < mib * MIB) items += 100;
        countCache.set(key, items);
    }
    return countCache.get(key);
}

/** Entry count of the oversized answer (the emulator check compares it with what the app imported). */
export function largeCatalogCount(kind, mib) {
    return countFor(kind, mib);
}

/**
 * Streams the catalogue of {@code kind} ('vod' or 'series') as a JSON array of about {@code mib}
 * MiB, or only the entries of {@code categoryId} (at most 500) when one is asked for.
 */
export async function writeLargeCatalog(res, kind, mib, categoryId) {
    const item = kind === 'vod' ? vodItem : seriesItem;
    const total = countFor(kind, mib);
    res.writeHead(200, { 'Content-Type': 'application/json' }); // chunked: real panels rarely declare a length
    let batch = '[';
    let written = 0;
    for (let i = 0; i < total; i++) {
        const entry = item(i);
        if (categoryId && entry.category_id !== categoryId) continue;
        if (categoryId && written === 500) break;
        batch += (written++ ? ',' : '') + JSON.stringify(entry);
        if (batch.length < 512 * 1024) continue;
        const flushed = res.write(batch);
        batch = '';
        if (!flushed) {
            await new Promise((resolve) => {
                const done = () => (res.off('drain', done), res.off('close', done), resolve());
                res.on('drain', done);
                res.on('close', done);
            });
        }
        if (res.destroyed) return; // the client went away
    }
    res.end(batch + ']');
}
