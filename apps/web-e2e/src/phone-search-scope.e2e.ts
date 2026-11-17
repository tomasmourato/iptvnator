import { type Locator } from '@playwright/test';
import { expect, test } from './pwa-mock.fixture';
import {
    addXtreamPortal,
    interceptPwaProviderRequests,
} from './sources-pwa.helpers';

/**
 * Searching inside a category on a phone.
 *
 * The header's scope chip is hidden at phone width, so a search inside a
 * category had no visible way out. A row under the header now offers the
 * category's matches and every category's matches, and switches between
 * them while keeping the search term.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/phone-search-scope.e2e.ts
 */

const PHONE = { width: 375, height: 812 };

async function countOf(segment: Locator): Promise<number> {
    return Number(
        (await segment.locator('.search-scope__count').textContent())?.trim()
    );
}

test.describe('xtream search scope on a phone', () => {
    test.beforeEach(async ({ page, pwaMocks }) => {
        await interceptPwaProviderRequests(page);
        await page.goto('/');
        await addXtreamPortal(page, 'Scope Portal', pwaMocks);
    });

    test('@mobile @xtream switches a category search to every category and back', async ({
        page,
    }) => {
        await page.setViewportSize(PHONE);
        await page.locator('[data-test-id="context-drawer-toggle"]').click();
        const firstCategory = page
            .locator('.context-panel .category-item')
            .first();
        await expect(firstCategory).toBeVisible();
        await firstCategory.click();
        await page.waitForURL(/\/vod\/\d+/);

        const firstTitle = page.locator('app-grid-list .title').first();
        await expect(firstTitle).toBeVisible();
        const term = ((await firstTitle.textContent()) ?? '')
            .trim()
            .slice(0, 3);
        expect(term.length).toBeGreaterThan(0);

        const scopeRow = page.locator(
            '[data-test-id="workspace-search-scope"]'
        );
        await expect(scopeRow).toBeHidden();
        await page.locator('#workspace-shell-search-input').fill(term);

        const category = page.locator(
            '[data-test-id="workspace-search-scope-category"]'
        );
        const all = page.locator('[data-test-id="workspace-search-scope-all"]');
        await expect(scopeRow).toBeVisible();
        await expect(category).toHaveAttribute('aria-pressed', 'true');
        await expect(category.locator('.search-scope__label')).not.toBeEmpty();
        const inCategory = await countOf(category);
        const everywhere = await countOf(all);
        expect(inCategory).toBeGreaterThan(0);
        expect(everywhere).toBeGreaterThanOrEqual(inCategory);

        await all.click();
        await expect(all).toHaveAttribute('aria-pressed', 'true');
        await expect(page).toHaveURL(/\/vod(\?|$)/);
        await expect(page).toHaveURL(
            new RegExp(`q=${encodeURIComponent(term)}`)
        );
        await expect(page.locator('#workspace-shell-search-input')).toHaveValue(
            term
        );

        await category.click();
        await expect(category).toHaveAttribute('aria-pressed', 'true');
        await expect(page).toHaveURL(/\/vod\/\d+/);
        await expect(page).toHaveURL(
            new RegExp(`q=${encodeURIComponent(term)}`)
        );
        expect(await countOf(category)).toBe(inCategory);
    });

    test('@mobile @xtream switches a live category search to every category and back', async ({
        page,
    }) => {
        await page.goto(page.url().replace(/\/vod.*$/, '/live'));
        await page.setViewportSize(PHONE);
        await page.locator('[data-test-id="context-drawer-toggle"]').click();
        await page.locator('.context-panel .category-item').first().click();

        const firstChannel = page
            .locator('[data-test-id="channel-item"] .channel-name')
            .first();
        await expect(firstChannel).toBeVisible();
        const term = ((await firstChannel.textContent()) ?? '')
            .trim()
            .slice(0, 3);
        await page.locator('#workspace-shell-search-input').fill(term);

        const category = page.locator(
            '[data-test-id="workspace-search-scope-category"]'
        );
        const all = page.locator('[data-test-id="workspace-search-scope-all"]');
        await expect(category).toHaveAttribute('aria-pressed', 'true');
        await expect(category.locator('.search-scope__label')).not.toBeEmpty();
        const inCategory = await countOf(category);
        const everywhere = await countOf(all);

        await all.click();
        await expect(all).toHaveAttribute('aria-pressed', 'true');
        await expect(page.locator('[data-test-id="channel-item"]')).toHaveCount(
            everywhere
        );

        await category.click();
        await expect(category).toHaveAttribute('aria-pressed', 'true');
        await expect(page.locator('[data-test-id="channel-item"]')).toHaveCount(
            inCategory
        );
    });

    test('@mobile @xtream keeps the row off wider screens', async ({
        page,
    }) => {
        await page.locator('.context-panel .category-item').first().click();
        await page.waitForURL(/\/vod\/\d+/);
        await page.locator('#workspace-shell-search-input').fill('a');

        await expect(
            page.locator('app-workspace-shell-header .search-chip--scope')
        ).toBeVisible();
        await expect(
            page.locator('app-workspace-shell-search-scope')
        ).toHaveCount(0);
    });
});
