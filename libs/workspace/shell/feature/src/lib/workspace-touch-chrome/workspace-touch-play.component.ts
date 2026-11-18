import {
    afterNextRender,
    ChangeDetectionStrategy,
    Component,
    computed,
    ElementRef,
    inject,
    Injector,
    input,
    signal,
    viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIcon } from '@angular/material/icon';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import type { PortalRailLink } from '@iptvnator/portal/shared/util';
import { filter } from 'rxjs';

/**
 * The touch chrome's Play (fork-only, tv-shell touch mode): one button for a
 * source's sections. It shows the current section, and a tap fans the
 * sections out (Movies / Live TV / Series, Radio on Stalker, All / Groups on
 * M3U) with the secondary links (Recently added, Search) below them: above
 * the bottom bar in portrait, beside the rail in landscape.
 *
 * The open dial is a Back layer: it carries `data-tv-back-layer` and closes
 * on Escape, which the tv-shell's Back sends before it navigates.
 */
@Component({
    selector: 'app-workspace-touch-play',
    imports: [MatIcon, RouterLink, TranslatePipe],
    templateUrl: './workspace-touch-play.component.html',
    styleUrl: './workspace-touch-play.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspaceTouchPlayComponent {
    private readonly router = inject(Router);
    private readonly injector = inject(Injector);
    private readonly trigger =
        viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
    private readonly dial = viewChild<ElementRef<HTMLElement>>('dial');

    readonly primary = input<PortalRailLink[]>([]);
    readonly secondary = input<PortalRailLink[]>([]);
    readonly currentSection = input<string | null>(null);
    readonly placement = input<'above' | 'beside'>('above');

    readonly open = signal(false);
    /** The section the route shows, when it is one of this source's. */
    readonly current = computed(
        () =>
            this.primary().find(
                (link) =>
                    !!link.section && link.section === this.currentSection()
            ) ?? null
    );

    constructor() {
        this.router.events
            .pipe(
                filter((event) => event instanceof NavigationEnd),
                takeUntilDestroyed()
            )
            .subscribe(() => this.open.set(false));
    }

    toggle(): void {
        // No source yet: Play leads to the place to add one.
        if (this.primary().length === 0) {
            void this.router.navigate(['/workspace/sources']);
            return;
        }
        if (this.open()) {
            this.close();
            return;
        }
        this.open.set(true);
        afterNextRender(
            () =>
                this.dial()
                    ?.nativeElement.querySelector<HTMLElement>(
                        '[role="menuitem"]'
                    )
                    ?.focus(),
            { injector: this.injector }
        );
    }

    close(restoreFocus = true): void {
        if (!this.open()) return;
        this.open.set(false);
        if (restoreFocus) this.trigger().nativeElement.focus();
    }

    /** Escape, or the tv-shell's Back: close the dial, nothing else. */
    onEscape(event: Event): void {
        event.preventDefault();
        event.stopPropagation();
        this.close();
    }
}
