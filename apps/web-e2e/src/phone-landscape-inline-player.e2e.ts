import { expect, test } from './pwa-mock.fixture';
import {
    addXtreamPortal,
    interceptPwaProviderRequests,
} from './sources-pwa.helpers';

/**
 * A movie's inline player on a phone in landscape. Between its system bars a
 * Pixel 8 is 864x359 CSS px, wider than the phone breakpoint, so the desktop
 * layout applies with the categories panel beside the detail. The player's
 * 220-320px floor plus its header put the bottom of the video below the
 * fold; on a short touch screen the floor goes and the header tightens.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/phone-landscape-inline-player.e2e.ts
 */

test.use({ hasTouch: true, isMobile: true });

test('@mobile @xtream movie inline player fits a landscape phone', async ({
    page,
    pwaMocks,
}) => {
    await interceptPwaProviderRequests(page);
    await page.goto('/');
    await addXtreamPortal(page, 'Landscape Portal', pwaMocks);
    await page.locator('.context-panel .category-item').first().click();
    await page.locator('app-grid-list mat-card').first().click();
    await page
        .locator('app-portal-detail-shell')
        .getByRole('button', { name: /^Play/ })
        .first()
        .click();

    const shell = page.locator('app-portal-inline-player .player-shell');
    await expect(shell).toBeVisible();
    await page.setViewportSize({ width: 864, height: 359 });
    // With the panel at the top of the content, under the 56px app header,
    // the whole video has to be on screen: header + stage within the rest.
    const available = 359 - 56;
    await expect(async () => {
        const panel = await shell.boundingBox();
        const stage = await shell
            .locator('.player-shell__viewport')
            .boundingBox();
        expect(panel).not.toBeNull();
        expect(stage).not.toBeNull();
        expect(stage!.height).toBeGreaterThan(150);
        expect(stage!.y + stage!.height - panel!.y).toBeLessThanOrEqual(
            available
        );
    }).toPass({ timeout: 5_000 });
});
