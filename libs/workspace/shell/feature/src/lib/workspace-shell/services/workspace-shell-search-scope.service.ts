import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { XtreamStore } from '@iptvnator/portal/xtream/data-access';
import { RuntimeCapabilitiesService } from '@iptvnator/services';
import {
    countXtreamSearchMatches,
    matchesSearchTerm,
    type XtreamScopeCountSource,
} from './helpers/workspace-shell-search-scope-count';
import { hasActiveLiveCategoryRoute } from '../../workspace-context-panel/workspace-context-panel-route.utils';
import { WorkspaceShellRouteStateService } from './workspace-shell-route-state.service';
import { WorkspaceShellSearchService } from './workspace-shell-search.service';

/** A category the user widened to "All categories", to switch back to. */
interface LeftScope {
    playlistId: string;
    section: string;
    categoryId: number;
    label: string;
}

export interface SearchScopeSegment {
    label: string;
    count: number;
    active: boolean;
}

/**
 * What the phone search scope row shows while a search runs inside a
 * category:
 * - `toggle` (Xtream): "Newsroom · 2 | All categories · 6". Both counts come
 *   from the streams the store already holds, so they cost a pass each.
 * - `link` (Stalker): the scope and an "All categories" action. Stalker
 *   searches VOD and series on the portal page by page, so an all-category
 *   count would cost an extra portal request per search.
 */
export type SearchScopeView =
    | {
          mode: 'toggle';
          category: SearchScopeSegment;
          all: SearchScopeSegment;
      }
    | { mode: 'link'; scopeLabel: string };

@Injectable()
export class WorkspaceShellSearchScopeService {
    private readonly router = inject(Router);
    private readonly xtreamStore = inject(XtreamStore);
    private readonly runtime = inject(RuntimeCapabilitiesService);
    private readonly routeState = inject(WorkspaceShellRouteStateService);
    private readonly search = inject(WorkspaceShellSearchService);

    private readonly leftScope = signal<LeftScope | null>(null);
    private readonly term = computed(() =>
        this.search.appliedSearchQuery().trim()
    );

    /** The left category, while the same section and search are still on. */
    private readonly returnScope = computed(() => {
        const left = this.leftScope();
        const route = this.routeState.currentRoute();
        return left &&
            route.context?.provider === 'xtreams' &&
            route.context.playlistId === left.playlistId &&
            route.section === left.section &&
            this.xtreamStore.selectedCategoryId() === null
            ? left
            : null;
    });

    readonly view = computed<SearchScopeView | null>(() => {
        const term = this.term();
        const context = this.routeState.currentContext();
        if (!term || !context) {
            return null;
        }
        const scoped = this.search.canClearCategoryScope();

        if (context.provider === 'stalker') {
            return scoped
                ? { mode: 'link', scopeLabel: this.search.searchScopeLabel() }
                : null;
        }
        if (context.provider !== 'xtreams') {
            return null;
        }

        const left = scoped ? null : this.returnScope();
        if (!scoped && !left) {
            return null;
        }
        const categoryId = scoped
            ? Number(this.xtreamStore.selectedCategoryId())
            : (left as LeftScope).categoryId;
        return {
            mode: 'toggle',
            category: {
                label: scoped ? this.categoryName() : (left as LeftScope).label,
                // While the category is on, its count is the list itself, with
                // the list's rating and watch filters.
                count: scoped
                    ? this.xtreamStore
                          .selectItemsFromSelectedCategory()
                          .filter((item) => matchesSearchTerm(item, term))
                          .length
                    : this.count(term, categoryId),
                active: scoped,
            },
            all: {
                label: '',
                count: this.count(term, null),
                active: !scoped,
            },
        };
    });

    /** Widens the search to every category, remembering the one it left. */
    selectAll(): void {
        const route = this.routeState.currentRoute();
        if (!this.search.canClearCategoryScope()) {
            return;
        }
        if (route.context?.provider === 'xtreams' && route.section) {
            this.leftScope.set({
                playlistId: route.context.playlistId,
                section: route.section,
                categoryId: Number(this.xtreamStore.selectedCategoryId()),
                label: this.categoryName(),
            });
        }
        this.search.clearCategoryScope();
    }

    /** Narrows the search back to the category it was widened from. */
    selectCategory(): void {
        const left = this.returnScope();
        if (!left) {
            return;
        }
        const term = this.search.searchQuery().trim();
        this.leftScope.set(null);
        this.xtreamStore.setSelectedCategory(left.categoryId);
        // Choosing a category clears the store's term; the search stays.
        this.xtreamStore.setCategorySearchTerm(term);

        const path = [
            '/workspace',
            'xtreams',
            left.playlistId,
            left.section,
            left.categoryId,
        ];
        const queryParams = { q: term || null };
        if (left.section !== 'live') {
            void this.router.navigate(path, { queryParams });
        } else if (
            hasActiveLiveCategoryRoute(this.router.routerState.snapshot.root)
        ) {
            void this.router.navigate(path, { queryParams, replaceUrl: true });
        }
    }

    /** The selected category's name: API categories carry `category_name`. */
    private categoryName(): string {
        const category = this.xtreamStore.getSelectedCategory();
        return String(category?.category_name ?? category?.name ?? '');
    }

    private count(term: string, categoryId: number | null): number {
        return countXtreamSearchMatches(
            this.xtreamStore as XtreamScopeCountSource,
            this.xtreamStore.selectedContentType(),
            term,
            {
                categoryId,
                supportsCategoryVisibility:
                    this.runtime.supportsXtreamSqliteDataSource,
            }
        );
    }
}
