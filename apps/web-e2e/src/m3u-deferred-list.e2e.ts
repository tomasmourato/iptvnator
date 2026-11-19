import { expect, test } from './pwa-mock.fixture';
import { interceptPwaProviderRequests } from './sources-pwa.helpers';

/**
 * The M3U channel list is a deferred block. A busy device can go a long
 * time without an idle period (on an Android emulator `requestIdleCallback`
 * never fired at all), and an idle-only trigger then left the list a
 * loading skeleton for good. This spec takes idle time away entirely and
 * expects the list anyway.
 */
test.describe('m3u channel list without browser idle time', () => {
    test.beforeEach(async ({ page }) => {
        await page.addInitScript(() => {
            // A starved device: idle callbacks are accepted and never run.
            window.requestIdleCallback = () => 0;
            window.cancelIdleCallback = () => undefined;
        });
        await interceptPwaProviderRequests(page);
        await page.goto('/');
    });

    test('@m3u renders the channel list without an idle period', async ({
        page,
    }) => {
        // Streams are never fetched: only the list is checked.
        await page.route('https://m3u-defer.fixture.invalid/**', (route) =>
            route.abort()
        );
        await page
            .getByRole('button', { name: 'Add playlist', exact: true })
            .click();
        const dialog = page.getByRole('dialog');
        await dialog.getByRole('radio', { name: /Raw m3u text/ }).click();
        const entries = [1, 2, 3].map(
            (n) =>
                `#EXTINF:-1,Deferred Channel ${n}\n` +
                `https://m3u-defer.fixture.invalid/${n}.m3u8`
        );
        await dialog
            .locator('textarea')
            .fill(['#EXTM3U', ...entries].join('\n'));
        await dialog
            .getByRole('button', { name: 'Add playlist', exact: true })
            .click();

        const layout = page.locator('app-video-player');
        await expect(layout).toBeVisible();
        await expect(
            layout.locator('[data-test-id="channel-item"]').first()
        ).toBeVisible({ timeout: 5_000 });
        await expect(layout.locator('app-sidebar')).toHaveCount(1);
    });
});
