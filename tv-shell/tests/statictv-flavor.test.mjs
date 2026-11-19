// Fork only: the StaticTV flavor must not present itself as IPTVnator. An upstream rebase can add
// a string naming IPTVnator or a new image to src/main; these checks fail until src/statictv
// overrides it.
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { test } from 'node:test';

const app = new URL('../android/app/', import.meta.url);
const main = new URL('src/main/res/', app);
const flavor = new URL('src/statictv/res/', app);
const read = (url) => readFileSync(url, 'utf8');

const strings = (xml) =>
    new Map([...xml.matchAll(/<string name="([^"]+)">([^<]*)<\/string>/g)].map(([, name, value]) => [name, value]));

const resourceNames = (dir) =>
    existsSync(dir) ? readdirSync(dir).map((file) => file.replace(/\.(png|webp|jpg|xml)$/, '')) : [];

for (const values of ['values', 'values-fr']) {
    test(`${values}: every string naming IPTVnator is overridden by StaticTV`, () => {
        const upstream = strings(read(new URL(`${values}/strings.xml`, main)));
        const fork = strings(read(new URL(`${values}/strings.xml`, flavor)));
        const named = [...upstream].filter(([, value]) => /iptvnator/i.test(value)).map(([name]) => name);
        assert.ok(named.includes('app_name'));
        for (const name of named) assert.ok(fork.has(name), `${values}/${name} is not overridden`);
        for (const [name, value] of fork) {
            assert.ok(upstream.has(name), `${values}/${name} overrides nothing in src/main`);
            assert.doesNotMatch(value, /iptvnator/i, `${values}/${name}`);
        }
        assert.equal(fork.get('app_name'), 'StaticTV');
    });
}

test('every image in src/main is replaced at the same qualifier', () => {
    const dirs = readdirSync(main).filter((dir) => /^(drawable|mipmap)/.test(dir));
    for (const dir of dirs) {
        const images = readdirSync(new URL(`${dir}/`, main)).filter((file) => /\.(png|webp|jpg)$/.test(file));
        const replaced = resourceNames(new URL(`${dir}/`, flavor));
        for (const image of images) {
            assert.ok(replaced.includes(image.replace(/\.\w+$/, '')), `${dir}/${image} is not replaced`);
        }
    }
    // The adaptive icon XMLs stay upstream's; their layers are replaced.
    assert.ok(resourceNames(new URL('drawable/', flavor)).includes('iptvnator_launcher_background'));
});

test('the launcher shortcut targets the StaticTV application ID', () => {
    const fork = read(new URL('fork.gradle', app));
    const applicationId = fork.match(/statictv \{[^}]*applicationId '([^']+)'/)?.[1];
    assert.equal(applicationId, 'io.github.tomasmourato.tv');
    const upstream = read(new URL('xml/shortcuts.xml', main));
    const shortcut = read(new URL('xml/shortcuts.xml', flavor));
    assert.match(shortcut, new RegExp(`android:targetPackage="${applicationId.replaceAll('.', '\\.')}"`));
    // Same action and class as upstream's shortcut: only the package differs.
    const attribute = (xml, name) => xml.match(new RegExp(`android:${name}="([^"]+)"`))?.[1];
    for (const name of ['action', 'targetClass', 'shortcutId', 'shortcutLongLabel']) {
        assert.equal(attribute(shortcut, name), attribute(upstream, name), name);
    }
});

test('StaticTV registers NextLib as the decoder provider, with its GPL notices', () => {
    const service = read(new URL('src/statictv/resources/META-INF/services/org.tvshell.iptvnator.player.VideoDecoderProvider', app)).trim();
    assert.equal(service, 'org.tvshell.iptvnator.player.NextLibDecoderProvider');
    assert.ok(existsSync(new URL(`src/statictv/java/${service.replaceAll('.', '/')}.java`, app)));
    assert.match(read(new URL('src/statictv/assets/tvshell/GPL-3.0.txt', app)), /GNU GENERAL PUBLIC LICENSE\s+Version 3, 29 June 2007/);
    const notices = read(new URL('src/statictv/assets/tvshell/NOTICES-StaticTV.txt', app));
    const nextlib = read(new URL('fork.gradle', app)).match(/statictvImplementation 'io\.github\.anilbeesetti:nextlib-media3ext:([\d.]+)-([\d.]+)'/);
    assert.ok(nextlib, 'NextLib is a statictv-only dependency');
    // NextLib's version names the Media3 release it is built against.
    assert.equal(nextlib[1], read(new URL('build.gradle', app)).match(/def media3Version = '([\d.]+)'/)?.[1]);
    assert.ok(notices.includes(`${nextlib[1]}-${nextlib[2]}`), 'the notices name the shipped NextLib version');
    // The provider and NextLib stay out of the upstream source set.
    assert.doesNotMatch(read(new URL('build.gradle', app)), /nextlib/i);
    assert.ok(!existsSync(new URL('src/main/resources/META-INF/services/org.tvshell.iptvnator.player.VideoDecoderProvider', app)));
});

test('app/build.gradle applies fork.gradle', () => {
    assert.match(read(new URL('build.gradle', app)), /^apply from: 'fork\.gradle'/m);
});
