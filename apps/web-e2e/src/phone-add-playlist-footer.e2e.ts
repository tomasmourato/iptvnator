import { type Locator } from '@playwright/test';
import { expect, test } from './pwa-mock.fixture';
import { interceptPwaProviderRequests } from './sources-pwa.helpers';

/**
 * The add-playlist footer on a phone. The dialog kit stacks one full-width
 * action per row at <=480px, which here meant four rows (Clear, Cancel, Test,
 * Add) under the Xtream notice. The filled actions stay full width with the
 * primary first; Clear and Cancel share one row below them.
 *
 * Tag: @mobile — run only this spec with:
 *   pnpm nx run web-e2e:e2e-ci--src/phone-add-playlist-footer.e2e.ts
 */

test.use({ viewport: { width: 390, height: 844 } });

async function box(locator: Locator) {
    const result = await locator.boundingBox();
    expect(result).not.toBeNull();
    return result!;
}

test('@mobile @sources Xtream footer keeps the actions on three rows', async ({
    page,
}) => {
    await interceptPwaProviderRequests(page);
    await page.goto('/');
    await page.getByRole('button', { name: 'Add playlist' }).click();
    const dialog = page.locator('mat-dialog-container');
    await dialog.getByRole('radio', { name: /Xtream credentials/i }).click();

    const footer = dialog.locator('.app-dialog__footer');
    await expect(footer).toBeVisible();
    const add = footer.getByRole('button', {
        name: 'Add playlist',
        exact: true,
    });
    const test_ = footer.getByRole('button', { name: /Test/ });
    const cancel = footer.getByRole('button', { name: 'Cancel', exact: true });
    const clear = footer.getByRole('button', { name: 'Clear', exact: true });

    const [addBox, testBox, cancelBox, clearBox, footerBox] = await Promise.all(
        [add, test_, cancel, clear, footer].map(box)
    );
    // Primary first, full width; the secondary below it, full width.
    expect(addBox.y).toBeLessThan(testBox.y);
    expect(addBox.width).toBeGreaterThan(footerBox.width * 0.8);
    expect(testBox.width).toBeGreaterThan(footerBox.width * 0.8);
    // The text actions share the last row.
    expect(Math.abs(cancelBox.y - clearBox.y)).toBeLessThanOrEqual(1);
    expect(cancelBox.y).toBeGreaterThan(testBox.y);
});
