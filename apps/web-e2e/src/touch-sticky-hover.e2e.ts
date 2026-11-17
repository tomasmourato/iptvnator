import { type Page } from '@playwright/test';
import { expect, test } from './pwa-mock.fixture';
import { interceptPwaProviderRequests } from './sources-pwa.helpers';

/**
 * Hover fills on touch screens. After a tap a touch screen keeps :hover on
 * the tapped element, so channel rows and the add-playlist method cards kept
 * their hover fill until the next tap elsewhere. The fills now need a pointer
 * that hovers; a mouse keeps them.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/touch-sticky-hover.e2e.ts
 */

const TRANSPARENT = 'rgba(0, 0, 0, 0)';

async function addRawPlaylist(page: Page): Promise<void> {
    await page.route('https://hover.fixture.invalid/**', (route) =>
        route.abort()
    );
    await page
        .getByRole('button', { name: 'Add playlist', exact: true })
        .click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('radio', { name: /Raw m3u text/ }).click();
    await dialog
        .locator('textarea')
        .fill(
            '#EXTM3U\n#EXTINF:-1,Hover Channel 1\nhttps://hover.fixture.invalid/1.m3u8\n#EXTINF:-1,Hover Channel 2\nhttps://hover.fixture.invalid/2.m3u8'
        );
    await dialog
        .getByRole('button', { name: 'Add playlist', exact: true })
        .click();
}

async function hoveredFill(page: Page, selector: string): Promise<string> {
    const target = page.locator(selector).first();
    await expect(target).toBeVisible({ timeout: 15_000 });
    await target.hover();
    // Past the 150ms background transition.
    await page.waitForTimeout(400);
    return target.evaluate((el) => getComputedStyle(el).backgroundColor);
}

test.describe('touch screen', () => {
    test.use({
        viewport: { width: 390, height: 844 },
        hasTouch: true,
        isMobile: true,
    });

    test('@mobile hover leaves no fill on rows and method cards', async ({
        page,
    }) => {
        await interceptPwaProviderRequests(page);
        await page.goto('/');
        await addRawPlaylist(page);
        expect(await hoveredFill(page, '[data-test-id="channel-item"]')).toBe(
            TRANSPARENT
        );

        await page
            .getByRole('button', { name: 'Add playlist' })
            .first()
            .click();
        expect(await hoveredFill(page, '.apd-method:not(.active)')).toBe(
            TRANSPARENT
        );
    });
});

test.describe('mouse', () => {
    test('@mobile hover keeps its fill on rows', async ({ page }) => {
        await interceptPwaProviderRequests(page);
        await page.goto('/');
        await addRawPlaylist(page);
        expect(
            await hoveredFill(page, '[data-test-id="channel-item"]')
        ).not.toBe(TRANSPARENT);
    });
});
