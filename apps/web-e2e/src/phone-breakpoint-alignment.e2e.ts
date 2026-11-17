import { expect, test } from './pwa-mock.fixture';
import { interceptPwaProviderRequests } from './sources-pwa.helpers';

/**
 * Phone rules use the 640px breakpoint (UI guidelines, "Phone Layout"). The
 * radio player's artwork and the series navigation controls used 599px, so
 * between 600 and 640px the shell had already stacked for a phone while
 * they kept their desktop sizes.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/phone-breakpoint-alignment.e2e.ts
 */

test.use({ viewport: { width: 620, height: 900 } });

test('@mobile @m3u radio artwork takes its phone size at 620px', async ({
    page,
}) => {
    await interceptPwaProviderRequests(page);
    await page.route('https://radio.fixture.invalid/**', (route) =>
        route.abort()
    );
    await page.goto('/');
    await page
        .getByRole('button', { name: 'Add playlist', exact: true })
        .click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('radio', { name: /Raw m3u text/ }).click();
    await dialog
        .locator('textarea')
        .fill(
            '#EXTM3U\n#EXTINF:-1 radio="true",Breakpoint Radio\nhttps://radio.fixture.invalid/one.mp3'
        );
    await dialog
        .getByRole('button', { name: 'Add playlist', exact: true })
        .click();

    await page
        .locator('[data-test-id="channel-item"]')
        .filter({ hasText: 'Breakpoint Radio' })
        .click();
    const artwork = page.locator('app-audio-player .artwork-fallback');
    await expect(artwork).toBeVisible({ timeout: 15_000 });
    await expect
        .poll(async () => (await artwork.boundingBox())?.width ?? 0)
        .toBe(160);
});
