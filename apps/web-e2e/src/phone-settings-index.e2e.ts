import { expect, test } from './pwa-mock.fixture';

/**
 * Settings on a phone open on the section list, the standard mobile
 * settings pattern, instead of General with every other section hidden in
 * the drawer. Before, the video player setting took the drawer toggle,
 * Playback, then the first row; the list now states the player right on
 * its Playback row.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/phone-settings-index.e2e.ts
 */

const PHONE = { width: 375, height: 812 };

test.describe('settings on a phone', () => {
    test.use({ viewport: PHONE });

    test('@mobile @settings opens on the section list and returns to it', async ({
        page,
    }) => {
        await page.goto('/workspace/settings');
        const index = page.locator('[data-test-id="settings-section-index"]');
        await expect(index).toBeVisible();
        await expect(page).toHaveURL(/\/workspace\/settings$/);
        await expect(
            page.locator('[data-test-id="settings-index-player"]')
        ).toBeVisible();

        await page.locator('[data-test-id="settings-index-playback"]').click();
        await expect(page).toHaveURL(/\/workspace\/settings\/playback$/);
        await expect(
            page.locator('[data-test-id="select-video-player"]')
        ).toBeVisible();

        await page.goBack();
        await expect(index).toBeVisible();
    });
});

test.describe('settings on a wider screen', () => {
    test.use({ viewport: { width: 1280, height: 800 } });

    test('@settings still opens on General', async ({ page }) => {
        await page.goto('/workspace/settings');
        await expect(page).toHaveURL(/\/workspace\/settings\/general$/);
        await expect(
            page.locator('[data-test-id="settings-section-index"]')
        ).toHaveCount(0);
    });
});
