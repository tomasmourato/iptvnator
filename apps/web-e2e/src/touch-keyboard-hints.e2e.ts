import { type Page } from '@playwright/test';
import { expect, test } from './pwa-mock.fixture';

/**
 * Keyboard-only affordances on touch screens.
 *
 * A phone in landscape (844x390, 915x412) is wider than the 640px phone
 * breakpoint, so width rules alone left the desktop hints on it: the Ctrl+K
 * label in the search field, the keyboard-shortcuts button and the ⌘K key
 * cap in the command palette. They follow the pointer instead: hidden on a
 * coarse pointer, kept for a mouse at the same size.
 *
 * Conventions: docs/architecture/iptvnator-ui-guidelines.md, "Phone Layout"
 * → "What to drop".
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/touch-keyboard-hints.e2e.ts
 */

const LANDSCAPE_PHONES = [
    { width: 844, height: 390 },
    { width: 915, height: 412 },
];

async function openSettings(page: Page): Promise<void> {
    // Settings shows the header search without any playlist.
    await page.goto('/workspace/settings');
    await expect(
        page.locator('app-workspace-shell-header .command-trigger')
    ).toBeVisible();
}

for (const viewport of LANDSCAPE_PHONES) {
    test.describe(`touch phone in landscape ${viewport.width}x${viewport.height}`, () => {
        test.use({ viewport, hasTouch: true, isMobile: true });

        test('@mobile hides the keyboard shortcut hints', async ({ page }) => {
            await openSettings(page);
            const header = page.locator('app-workspace-shell-header');

            expect(
                await page.evaluate(
                    () => matchMedia('(pointer: coarse)').matches
                )
            ).toBe(true);
            await expect(
                header.locator('.command-trigger__shortcut')
            ).toBeHidden();
            // The palette stays reachable by touch: the label becomes an icon.
            await expect(
                header.locator('.command-trigger__icon')
            ).toBeVisible();
            await expect(header.locator('.shortcuts-trigger')).toBeHidden();

            await header.locator('.command-trigger').click();
            const palette = page.locator('app-workspace-command-palette');
            await expect(palette).toBeVisible();
            await expect(palette.locator('.palette-search__kbd')).toBeHidden();
        });
    });
}

test.describe('mouse at landscape phone size 844x390', () => {
    test.use({ viewport: LANDSCAPE_PHONES[0], hasTouch: false });

    test('@mobile keeps the keyboard shortcut hints', async ({ page }) => {
        await openSettings(page);
        const header = page.locator('app-workspace-shell-header');

        await expect(
            header.locator('.command-trigger__shortcut')
        ).toBeVisible();
        await expect(header.locator('.shortcuts-trigger')).toBeVisible();

        await header.locator('.command-trigger').click();
        const palette = page.locator('app-workspace-command-palette');
        await expect(palette).toBeVisible();
        await expect(palette.locator('.palette-search__kbd')).toBeVisible();
    });
});
