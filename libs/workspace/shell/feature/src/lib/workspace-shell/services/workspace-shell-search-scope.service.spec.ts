import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { XtreamStore } from '@iptvnator/portal/xtream/data-access';
import { RuntimeCapabilitiesService } from '@iptvnator/services';
import { parseWorkspaceShellRoute } from '@iptvnator/workspace/shell/util';
import { WorkspaceShellRouteStateService } from './workspace-shell-route-state.service';
import { WorkspaceShellSearchScopeService } from './workspace-shell-search-scope.service';
import { WorkspaceShellSearchService } from './workspace-shell-search.service';

const LIVE = [
    ['Aurora News', 1],
    ['Civic One', 1],
    ['Fieldside Sports', 2],
    ['Motion Arena', 2],
    ['Horizon Kids', 3],
    ['Kitchen Lab', 3],
    ['Atlas Docs', 4],
    ['Night Music', 4],
].map(([name, category], index) => ({
    stream_id: index + 1,
    name,
    title: name,
    category_id: String(category),
}));

describe('WorkspaceShellSearchScopeService', () => {
    let service: WorkspaceShellSearchScopeService;
    const currentRoute = signal(
        parseWorkspaceShellRoute('/workspace/xtreams/pl-1/live')
    );
    const appliedSearchQuery = signal('o');
    const canClearCategoryScope = signal(true);
    const selectedCategoryId = signal<number | null>(1);
    const clearCategoryScope = jest.fn(() => {
        selectedCategoryId.set(null);
        canClearCategoryScope.set(false);
    });
    const navigate = jest.fn();
    const store = {
        liveStreams: signal(LIVE),
        liveCategories: signal([]),
        vodStreams: signal([]),
        serialStreams: signal([]),
        vodCategories: signal([]),
        serialCategories: signal([]),
        selectedContentType: signal('live'),
        selectedCategoryId,
        selectItemsFromSelectedCategory: () =>
            LIVE.filter(
                (item) => Number(item.category_id) === selectedCategoryId()
            ),
        getSelectedCategory: () => ({
            category_id: '1',
            category_name: 'Newsroom',
        }),
        setSelectedCategory: jest.fn((id: number | null) =>
            selectedCategoryId.set(id)
        ),
        setCategorySearchTerm: jest.fn(),
    };

    beforeEach(() => {
        jest.clearAllMocks();
        currentRoute.set(
            parseWorkspaceShellRoute('/workspace/xtreams/pl-1/live')
        );
        appliedSearchQuery.set('o');
        canClearCategoryScope.set(true);
        selectedCategoryId.set(1);
        TestBed.configureTestingModule({
            providers: [
                WorkspaceShellSearchScopeService,
                {
                    provide: Router,
                    useValue: {
                        navigate,
                        routerState: {
                            snapshot: { root: { url: [], children: [] } },
                        },
                    },
                },
                { provide: XtreamStore, useValue: store },
                {
                    provide: RuntimeCapabilitiesService,
                    useValue: { supportsXtreamSqliteDataSource: false },
                },
                {
                    provide: WorkspaceShellRouteStateService,
                    useValue: {
                        currentRoute,
                        currentContext: () => currentRoute().context,
                    },
                },
                {
                    provide: WorkspaceShellSearchService,
                    useValue: {
                        appliedSearchQuery,
                        searchQuery: appliedSearchQuery,
                        canClearCategoryScope,
                        searchScopeLabel: () => 'Live TV / Newsroom',
                        clearCategoryScope,
                    },
                },
            ],
        });
        service = TestBed.inject(WorkspaceShellSearchScopeService);
    });

    it('counts matches in the category and in every category', () => {
        expect(service.view()).toEqual({
            mode: 'toggle',
            category: { label: 'Newsroom', count: 2, active: true },
            all: { label: '', count: 6, active: false },
        });
    });

    it('shows nothing without a search term', () => {
        appliedSearchQuery.set('  ');
        expect(service.view()).toBeNull();
    });

    it('widens to every category and keeps the way back', () => {
        service.selectAll();

        expect(clearCategoryScope).toHaveBeenCalled();
        expect(service.view()).toEqual({
            mode: 'toggle',
            category: { label: 'Newsroom', count: 2, active: false },
            all: { label: '', count: 6, active: true },
        });
    });

    it('narrows back to the category with the same search', () => {
        currentRoute.set(
            parseWorkspaceShellRoute('/workspace/xtreams/pl-1/vod')
        );
        service.selectAll();

        service.selectCategory();

        expect(store.setSelectedCategory).toHaveBeenCalledWith(1);
        expect(store.setCategorySearchTerm).toHaveBeenCalledWith('o');
        expect(navigate).toHaveBeenCalledWith(
            ['/workspace', 'xtreams', 'pl-1', 'vod', 1],
            { queryParams: { q: 'o' } }
        );
    });

    it('forgets the category once the section changes', () => {
        service.selectAll();
        currentRoute.set(
            parseWorkspaceShellRoute('/workspace/xtreams/pl-1/series')
        );

        expect(service.view()).toBeNull();
    });

    it('offers only the way out on Stalker, without counts', () => {
        currentRoute.set(
            parseWorkspaceShellRoute('/workspace/stalker/pl-2/vod')
        );

        expect(service.view()).toEqual({
            mode: 'link',
            scopeLabel: 'Live TV / Newsroom',
        });
    });
});
