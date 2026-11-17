import { type Locator, type Page } from '@playwright/test';
import { expect, test } from './pwa-mock.fixture';
import {
    addXtreamPortal,
    interceptPwaProviderRequests,
} from './sources-pwa.helpers';

/**
 * A playing live channel on a phone: the player on top, then Channels /
 * Programme guide tabs, one pane at a time. Before, the channel list kept
 * 42vh above a player squeezed to about 100px and the guide below it.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/phone-live-tabs.e2e.ts
 */

const PHONE = { width: 375, height: 812 };

async function bottomGap(page: Page, pane: Locator): Promise<number> {
    const content = await page.locator('main.workspace-content').boundingBox();
    const box = await pane.boundingBox();
    if (!content || !box) return Number.POSITIVE_INFINITY;
    return Math.abs(box.y + box.height - (content.y + content.height));
}

test.describe('xtream live tabs on a phone', () => {
    test.beforeEach(async ({ page, pwaMocks }) => {
        await interceptPwaProviderRequests(page);
        await page.goto('/');
        await addXtreamPortal(page, 'Tabs Portal', pwaMocks);
        await page.goto(page.url().replace(/\/vod.*$/, '/live'));
        await page.locator('.context-panel .category-item').first().click();
    });

    test('@mobile @xtream shows the list or the guide under the player', async ({
        page,
    }) => {
        await page.setViewportSize(PHONE);
        const layout = page.locator('app-live-stream-layout');
        await layout.locator('[data-test-id="channel-item"]').first().click();

        const channelsTab = page.locator(
            '[data-test-id="live-phone-tab-channels"]'
        );
        const guideTab = page.locator('[data-test-id="live-phone-tab-guide"]');
        const sidebar = layout.locator('.sidebar');
        const epg = layout.locator('.content-container > .epg');
        await expect(channelsTab).toHaveAttribute('aria-selected', 'true');
        await expect(sidebar).toBeVisible();
        await expect(epg).toBeHidden();

        // The tabs sit right under the player, the list right under them.
        const player = await layout
            .locator('.content-container > .video-player')
            .boundingBox();
        const tabs = await page.locator('app-live-phone-tabs').boundingBox();
        expect(player && tabs).toBeTruthy();
        expect(
            Math.abs((player?.y ?? 0) + (player?.height ?? 0) - (tabs?.y ?? 0))
        ).toBeLessThanOrEqual(1);
        await expect
            .poll(() => bottomGap(page, sidebar))
            .toBeLessThanOrEqual(1);

        await guideTab.click();
        await expect(guideTab).toHaveAttribute('aria-selected', 'true');
        await expect(sidebar).toBeHidden();
        await expect(epg).toBeVisible();
        await expect.poll(() => bottomGap(page, epg)).toBeLessThanOrEqual(1);

        await channelsTab.click();
        await expect(sidebar).toBeVisible();
        await expect(epg).toBeHidden();
    });

    test('@mobile @xtream keeps the side-by-side layout on wider screens', async ({
        page,
    }) => {
        const layout = page.locator('app-live-stream-layout');
        await layout.locator('[data-test-id="channel-item"]').first().click();

        await expect(layout.locator('.content-container > .epg')).toBeVisible();
        await expect(layout.locator('.sidebar')).toBeVisible();
        await expect(
            page.locator('app-live-phone-tabs [role="tab"]')
        ).toHaveCount(0);
    });
});
