import { type Page } from '@playwright/test';
import { expect, test } from './pwa-mock.fixture';
import {
    addXtreamPortal,
    interceptPwaProviderRequests,
} from './sources-pwa.helpers';

/**
 * Fork-only touch chrome (tv-shell touch mode). The Android shell publishes a
 * capability descriptor (`__IPTVNATOR_CONFIG__.SHELL`); in touch mode a phone
 * in portrait gets a bottom bar with a speed-dial Play instead of the top bar,
 * and Settings moves to a header gear; held sideways it gets the compact
 * landscape (Play in the rail, the context panel as a drawer). Browsers, Electron and TV mode keep the
 * regular shell. The web app-config.js keeps a config set before it runs, so
 * the descriptor is injected the way the shell's own config would be.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/touch-chrome.e2e.ts
 */

const PHONE = { width: 390, height: 844 };
const PHONE_LANDSCAPE = { width: 844, height: 390 };

async function shellMode(page: Page, mode: 'touch' | 'tv'): Promise<void> {
    // app-config.js (the real one, or a test's own) assigns the whole config
    // object: keep the descriptor through that assignment.
    await page.addInitScript((shellModeValue) => {
        const SHELL = {
            kind: 'android-tv-shell',
            mode: shellModeValue,
            features: { externalPlayer: true, backupExport: false },
        };
        let config: Record<string, unknown> = { SHELL };
        Object.defineProperty(window, '__IPTVNATOR_CONFIG__', {
            configurable: true,
            get: () => config,
            set: (value: Record<string, unknown>) => {
                config = { ...(value ?? {}), SHELL };
            },
        });
    }, mode);
}

async function bottomOf(page: Page, selector: string): Promise<number> {
    const box = await page.locator(selector).first().boundingBox();
    expect(box, selector).not.toBeNull();
    return box!.y + box!.height;
}

test.describe('tv-shell touch mode, phone in portrait', () => {
    test.use({ viewport: PHONE, hasTouch: true, isMobile: true });

    test.beforeEach(async ({ page, pwaMocks }) => {
        await shellMode(page, 'touch');
        await interceptPwaProviderRequests(page);
        await page.goto('/');
        await addXtreamPortal(page, 'Touch Chrome Portal', pwaMocks);
    });

    test('@mobile bottom bar, the Play dial and the header gear replace the top bar', async ({
        page,
    }) => {
        const bar = page.locator('[data-test-id="touch-bottom-bar"]');
        await expect(bar).toBeVisible();
        await expect(page.locator('app-workspace-shell-rail')).toBeHidden();
        expect(
            await bottomOf(page, 'main.workspace-content')
        ).toBeLessThanOrEqual((await bar.boundingBox())!.y + 1);

        const play = page.locator('[data-test-id="touch-play"]');
        await expect(play).toContainText('Movies');
        await play.click();
        const dial = page.locator('[data-test-id="touch-play-dial"]');
        await expect(dial.getByRole('menuitem')).toContainText([
            'Movies',
            'Live TV',
            'Series',
        ]);
        await dial.getByRole('menuitem', { name: 'Live TV' }).click();
        await expect(page).toHaveURL(/\/live/);
        await expect(dial).toHaveCount(0);
        await expect(play).toContainText('Live TV');

        await page.locator('[data-test-id="header-touch-settings"]').click();
        await expect(page).toHaveURL(/\/workspace\/settings$/);
        await expect(
            page.locator('[data-test-id="settings-section-index"]')
        ).toBeVisible();
    });

    test('@mobile the header search icon expands the field with the category scope', async ({
        page,
    }) => {
        await page.locator('[data-test-id="context-drawer-toggle"]').click();
        await page.locator('.context-panel .category-item').first().click();
        await page.waitForURL(/\/vod\/\d+/);
        const categoryUrl = page.url();
        const firstTitle = page.locator('app-grid-list .title').first();
        await expect(firstTitle).toBeVisible();
        const term = ((await firstTitle.textContent()) ?? '')
            .trim()
            .slice(0, 3)
            .trim();

        const input = page.locator('#workspace-shell-search-input');
        const toggle = page.locator('[data-test-id="header-touch-search"]');
        await expect(input).toHaveCount(0);
        await toggle.click();
        await expect(input).toBeFocused();
        await input.fill(term);

        const field = page.locator(
            '[data-test-id="header-touch-search-field"]'
        );
        await expect(field).toHaveAttribute('data-tv-back-layer', '');
        // The field has the header row, so the placeholder and the chip fit.
        expect((await field.boundingBox())?.width ?? 0).toBeGreaterThan(300);
        await expect(
            page.locator('[data-test-id="playlist-switcher-trigger"]')
        ).toBeHidden();
        await expect(
            field.locator('[data-test-id="workspace-search-scope-clear"]')
        ).toBeVisible();
        await expect(
            page.locator('[data-test-id="workspace-search-scope"]')
        ).toBeVisible();
        await expect(page).toHaveURL(
            new RegExp(`q=${encodeURIComponent(term)}`)
        );

        // Escape is what the tv-shell's Back sends to an open layer: it clears
        // and collapses without leaving the category.
        await input.press('Escape');
        await expect(field).toHaveCount(0);
        await expect(toggle).toBeFocused();
        await expect(page).not.toHaveURL(/[?&]q=/);
        expect(page.url().split('?')[0]).toBe(categoryUrl.split('?')[0]);
    });

    test('@mobile the bar covers neither the live guide tab nor snack bars', async ({
        page,
    }) => {
        await page.goto(page.url().replace(/\/vod.*$/, '/live'));
        await page.locator('[data-test-id="context-drawer-toggle"]').click();
        await page.locator('.context-panel .category-item').first().click();
        await page
            .locator('app-live-stream-layout [data-test-id="channel-item"]')
            .first()
            .click();
        await page.locator('[data-test-id="live-phone-tab-guide"]').click();

        const barTop = (await page
            .locator('[data-test-id="touch-bottom-bar"]')
            .boundingBox())!.y;
        expect(
            await bottomOf(
                page,
                'app-live-stream-layout .content-container > .epg'
            )
        ).toBeLessThanOrEqual(barTop + 1);

        // A snack bar is lifted above the bar.
        const lift = await page.evaluate(() => {
            const toast = document.createElement('div');
            toast.className = 'mat-mdc-snack-bar-container';
            document.body.append(toast);
            const margin = getComputedStyle(toast).marginBottom;
            toast.remove();
            return margin;
        });
        expect(lift).toBe('72px');
    });
});

/** The topmost element at the centre of `selector` belongs to it. */
async function isOnTop(page: Page, selector: string): Promise<boolean> {
    return page
        .locator(selector)
        .first()
        .evaluate((element) => {
            const box = element.getBoundingClientRect();
            const hit = document.elementFromPoint(
                box.left + box.width / 2,
                box.top + box.height / 2
            );
            return !!hit && element.contains(hit);
        });
}

test.describe('tv-shell touch mode, phone held sideways', () => {
    test.use({ viewport: PHONE_LANDSCAPE, hasTouch: true, isMobile: true });

    test.beforeEach(async ({ page, pwaMocks }) => {
        await shellMode(page, 'touch');
        await interceptPwaProviderRequests(page);
        await page.goto('/');
        await addXtreamPortal(page, 'Touch Landscape Portal', pwaMocks);
    });

    test('@mobile Play takes the rail and the context panel becomes the drawer', async ({
        page,
    }) => {
        const rail = page.locator('app-workspace-shell-rail');
        const play = rail.locator('[data-test-id="touch-play"]');
        await expect(play).toBeVisible();
        await expect(rail.locator('.rail-context-region')).toHaveCount(0);
        await expect(
            page.locator('[data-test-id="touch-bottom-bar"]')
        ).toHaveCount(0);

        // Out of the layout until opened: the content starts at the rail.
        const sidebar = page.locator('app-workspace-shell-context-sidebar');
        await expect(sidebar).toBeHidden();
        const railRight = (await rail.boundingBox())!.width;
        expect(
            (await page.locator('main.workspace-content').boundingBox())!.x
        ).toBeLessThanOrEqual(railRight + 1);

        await play.click();
        const dial = page.locator('[data-test-id="touch-play-dial"]');
        expect((await dial.boundingBox())!.x).toBeGreaterThan(railRight);
        await dial.getByRole('menuitem', { name: 'Live TV' }).click();
        await expect(page).toHaveURL(/\/live/);
        await expect(dial).toHaveCount(0);

        await page.locator('[data-test-id="context-drawer-toggle"]').click();
        await expect(sidebar).toBeVisible();
        expect((await sidebar.boundingBox())!.width).toBeLessThanOrEqual(320);
        await expect(sidebar).toHaveAttribute('aria-modal', 'true');
        await sidebar.locator('.category-item').first().click();
        await expect(sidebar).toBeHidden();
    });

    test('@mobile live list beside a player with most of the height, under the dial and the drawer', async ({
        page,
    }) => {
        await page.goto(page.url().replace(/\/vod.*$/, '/live'));
        await page.locator('[data-test-id="context-drawer-toggle"]').click();
        await page.locator('.context-panel .category-item').first().click();
        await page
            .locator('app-live-stream-layout [data-test-id="channel-item"]')
            .first()
            .click();

        const layout = page.locator('app-live-stream-layout');
        const list = (await layout.locator('.sidebar').boundingBox())!;
        expect(Math.round(list.width)).toBe(320);
        const column = (await layout
            .locator('.content-container')
            .boundingBox())!;
        const video = (await layout.locator('.video-player').boundingBox())!;
        expect(video.height).toBeGreaterThanOrEqual(column.height * 0.6 - 1);
        await expect(layout.locator('.epg.epg--inline')).toBeVisible();

        // The dial and the drawer paint over the playing video.
        await page.locator('[data-test-id="touch-play"]').click();
        await expect
            .poll(() => isOnTop(page, '[data-test-id="touch-play-dial"] a'))
            .toBe(true);
        await page.keyboard.press('Escape');
        await expect(
            page.locator('[data-test-id="touch-play-dial"]')
        ).toHaveCount(0);
        await page.locator('[data-test-id="context-drawer-toggle"]').click();
        // Polled: the drawer slides in for 200ms.
        await expect
            .poll(() => isOnTop(page, 'app-workspace-shell-context-sidebar'))
            .toBe(true);

        // No bottom bar here, so snack bars keep the screen edge.
        const lift = await page.evaluate(() => {
            const toast = document.createElement('div');
            toast.className = 'mat-mdc-snack-bar-container';
            document.body.append(toast);
            const margin = getComputedStyle(toast).marginBottom;
            toast.remove();
            return margin;
        });
        expect(lift).toBe('0px');
    });
});

for (const mode of ['tv', null] as const) {
    test.describe(`${mode ?? 'no'} shell descriptor at phone size`, () => {
        test.use({ viewport: PHONE, hasTouch: true, isMobile: true });

        test('@mobile keeps the regular top bar', async ({ page }) => {
            if (mode) await shellMode(page, mode);
            await page.goto('/workspace/settings');
            await expect(page.locator('.app-rail')).toBeVisible();
            await expect(
                page.locator('[data-test-id="touch-bottom-bar"]')
            ).toHaveCount(0);
            await expect(
                page.locator('[data-test-id="header-touch-settings"]')
            ).toHaveCount(0);
        });
    });
}
