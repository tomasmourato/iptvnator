import { foldSearchText } from '@iptvnator/shared/interfaces';

interface CountableItem {
    title?: string | number | null;
    name?: string | number | null;
    category_id?: string | number | null;
}

interface CountableCategory {
    category_id?: string | number | null;
    id?: string | number | null;
    hidden?: boolean;
}

/** The Xtream store signals the counts read. */
export interface XtreamScopeCountSource {
    liveStreams: () => CountableItem[];
    vodStreams: () => CountableItem[];
    serialStreams: () => CountableItem[];
    liveCategories: () => CountableCategory[];
    vodCategories: () => CountableCategory[];
    serialCategories: () => CountableCategory[];
}

/** Whether an item's title matches the term the way the lists match it. */
export function matchesSearchTerm(item: CountableItem, term: string): boolean {
    const folded = foldSearchText(term.trim());
    return (
        !folded ||
        foldSearchText(String(item.title ?? item.name ?? '')).includes(folded)
    );
}

/**
 * Search matches in one Xtream category, or in every category (`null`): the
 * counts of the phone search scope toggle, over the streams the store already
 * holds. Changing category resets the store's rating filter, so a plain
 * search count is what the list shows once the user switches.
 *
 * The category visibility rule mirrors the store's `withSelection` (hidden
 * categories drop out where the source supports them). It is repeated here,
 * in the lazy shell, because exporting the store's own helpers would add
 * them to the startup bundle's exports.
 */
export function countXtreamSearchMatches(
    store: XtreamScopeCountSource,
    contentType: 'live' | 'vod' | 'series',
    term: string,
    options: { categoryId: number | null; supportsCategoryVisibility: boolean }
): number {
    const [items, categories] =
        contentType === 'live'
            ? [store.liveStreams(), store.liveCategories()]
            : contentType === 'vod'
              ? [store.vodStreams(), store.vodCategories()]
              : [store.serialStreams(), store.serialCategories()];
    const visibleIds = options.supportsCategoryVisibility
        ? new Set(
              categories
                  .filter((category) => !category.hidden)
                  .map((category) =>
                      Number(category.category_id ?? category.id)
                  )
          )
        : null;
    return items.filter((item) => {
        const categoryId = Number(item.category_id);
        return (
            (!visibleIds || visibleIds.has(categoryId)) &&
            (options.categoryId === null ||
                categoryId === options.categoryId) &&
            matchesSearchTerm(item, term)
        );
    }).length;
}
