import {
    countXtreamSearchMatches,
    type XtreamScopeCountSource,
} from './workspace-shell-search-scope-count';

const movie = (name: string, category: number) => ({
    name,
    title: name,
    category_id: String(category),
});

const store: XtreamScopeCountSource = {
    liveStreams: () => [],
    serialStreams: () => [],
    liveCategories: () => [],
    serialCategories: () => [],
    vodStreams: () => [
        movie('Black Harbor', 1),
        movie('Harbor Lights', 2),
        movie('Quiet Harbor', 3),
        movie('Summer Static', 1),
    ],
    vodCategories: () => [
        { category_id: '1' },
        { category_id: '2' },
        { category_id: '3', hidden: true },
    ],
};

describe('countXtreamSearchMatches', () => {
    const options = { categoryId: null, supportsCategoryVisibility: false };

    it('counts one category or every category', () => {
        expect(
            countXtreamSearchMatches(store, 'vod', 'harbor', {
                ...options,
                categoryId: 1,
            })
        ).toBe(1);
        expect(countXtreamSearchMatches(store, 'vod', 'harbor', options)).toBe(
            3
        );
    });

    it('leaves hidden categories out where the source supports them', () => {
        expect(
            countXtreamSearchMatches(store, 'vod', 'harbor', {
                ...options,
                supportsCategoryVisibility: true,
            })
        ).toBe(2);
    });

    it('matches the search the way the lists do, ignoring case', () => {
        expect(countXtreamSearchMatches(store, 'vod', 'HARBOR', options)).toBe(
            3
        );
    });
});
