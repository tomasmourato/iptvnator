import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import type { PortalRailLink } from '@iptvnator/portal/shared/util';
import { WorkspaceTouchPlayComponent } from './workspace-touch-play.component';

@Component({ template: '' })
class BlankComponent {}

const PRIMARY: PortalRailLink[] = [
    {
        icon: 'movie',
        tooltip: 'Movies',
        path: ['/workspace', 'xtreams', 'p', 'vod'],
        section: 'vod',
    },
    {
        icon: 'live_tv',
        tooltip: 'Live TV',
        path: ['/workspace', 'xtreams', 'p', 'live'],
        section: 'live',
    },
    {
        icon: 'tv',
        tooltip: 'Series',
        path: ['/workspace', 'xtreams', 'p', 'series'],
        section: 'series',
    },
];
const SECONDARY: PortalRailLink[] = [
    {
        icon: 'search',
        tooltip: 'Search',
        path: ['/workspace', 'xtreams', 'p', 'search'],
        section: 'search',
    },
];

@Component({
    imports: [WorkspaceTouchPlayComponent],
    template: `<app-workspace-touch-play
        [primary]="primary()"
        [secondary]="secondary"
        [currentSection]="section()"
    />`,
})
class HostComponent {
    readonly primary = signal(PRIMARY);
    readonly secondary = SECONDARY;
    readonly section = signal<string | null>('live');
}

describe('WorkspaceTouchPlayComponent', () => {
    function render() {
        TestBed.configureTestingModule({
            imports: [HostComponent, TranslateModule.forRoot()],
            providers: [
                provideRouter([{ path: '**', component: BlankComponent }]),
            ],
        });
        const fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        const el = fixture.nativeElement as HTMLElement;
        const play = () =>
            el.querySelector<HTMLButtonElement>('[data-test-id="touch-play"]')!;
        const dial = () => el.querySelector('[data-test-id="touch-play-dial"]');
        return { fixture, el, play, dial };
    }

    it('shows the current section and fans the sections out as a menu', () => {
        const { fixture, play, dial } = render();
        expect(play().textContent).toContain('Live TV');
        expect(play().getAttribute('aria-expanded')).toBe('false');

        play().click();
        fixture.detectChanges();

        expect(play().getAttribute('aria-expanded')).toBe('true');
        expect(dial()?.getAttribute('role')).toBe('menu');
        expect(dial()?.hasAttribute('data-tv-back-layer')).toBe(true);
        const items = [...dial()!.querySelectorAll('[role="menuitem"]')].map(
            (item) => item.textContent?.trim()
        );
        expect(items).toEqual([
            'movieMovies',
            'live_tvLive TV',
            'tvSeries',
            'Search',
        ]);
    });

    it('closes on Escape (the tv-shell Back) without letting it through', () => {
        const { fixture, play, dial } = render();
        play().click();
        fixture.detectChanges();
        const escape = new KeyboardEvent('keydown', {
            key: 'Escape',
            bubbles: true,
            cancelable: true,
        });
        const outside = jest.fn();
        document.addEventListener('keydown', outside);

        dial()!.dispatchEvent(escape);
        fixture.detectChanges();

        expect(dial()).toBeNull();
        expect(escape.defaultPrevented).toBe(true);
        expect(outside).not.toHaveBeenCalled();
        document.removeEventListener('keydown', outside);
    });

    it('closes when the route changes', async () => {
        const { fixture, play, dial } = render();
        play().click();
        fixture.detectChanges();

        await TestBed.inject(Router).navigateByUrl('/workspace/sources');
        fixture.detectChanges();

        expect(dial()).toBeNull();
    });

    it('leads to Sources when there is no source to play', () => {
        const { fixture, play, dial } = render();
        const router = TestBed.inject(Router);
        const navigate = jest.spyOn(router, 'navigate');
        fixture.componentInstance.primary.set([]);
        fixture.detectChanges();

        play().click();
        fixture.detectChanges();

        expect(navigate).toHaveBeenCalledWith(['/workspace/sources']);
        expect(dial()).toBeNull();
    });
});
