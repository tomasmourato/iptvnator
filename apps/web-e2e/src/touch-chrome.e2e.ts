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
 * and Settings moves to a header gear. Browsers, Electron and TV mode keep the
 * regular shell. The web app-config.js keeps a config set before it runs, so
 * the descriptor is injected the way the shell's own config would be.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/touch-chrome.e2e.ts
 */

const PHONE = { width: 390, height: 844 };

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
