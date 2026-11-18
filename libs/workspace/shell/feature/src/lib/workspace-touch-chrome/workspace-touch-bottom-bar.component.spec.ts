import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import type { PortalRailLink } from '@iptvnator/portal/shared/util';
import { WorkspaceTouchBottomBarComponent } from './workspace-touch-bottom-bar.component';

const WORKSPACE: PortalRailLink[] = [
    {
        icon: 'dashboard',
        tooltip: 'Dashboard',
        path: ['/workspace/dashboard'],
        exact: true,
    },
    { icon: 'library_books', tooltip: 'Sources', path: ['/workspace/sources'] },
    {
        icon: 'search',
        tooltip: 'Global search',
        path: ['/workspace/search'],
        exact: true,
    },
    {
        icon: 'favorite',
        tooltip: 'Global favorites',
        path: ['/workspace/global-favorites'],
        exact: true,
    },
    {
        icon: 'history',
        tooltip: 'Recently viewed',
        path: ['/workspace/global-recent'],
        exact: true,
    },
];

describe('WorkspaceTouchBottomBarComponent', () => {
    function render(links: PortalRailLink[]) {
        TestBed.configureTestingModule({
            imports: [
                WorkspaceTouchBottomBarComponent,
                TranslateModule.forRoot(),
            ],
            providers: [provideRouter([])],
        });
        const fixture = TestBed.createComponent(
            WorkspaceTouchBottomBarComponent
        );
        fixture.componentRef.setInput('workspaceLinks', links);
        fixture.detectChanges();
        const bar = fixture.nativeElement.querySelector(
            '[data-test-id="touch-bottom-bar"]'
        ) as HTMLElement;
        return [...bar.children].map(
            (child) =>
                child.getAttribute('data-test-id') ??
                child
                    .querySelector('[data-test-id]')
                    ?.getAttribute('data-test-id')
        );
    }

    it('lays out Home · Sources · Play · Favorites · Recent', () => {
        expect(render(WORKSPACE)).toEqual([
            'touch-bar-dashboard',
            'touch-bar-sources',
            'touch-play',
            'touch-bar-global-favorites',
            'touch-bar-global-recent',
        ]);
    });

    it('starts at Sources when the dashboard is off', () => {
        expect(render(WORKSPACE.slice(1))[0]).toBe('touch-bar-sources');
    });
});
