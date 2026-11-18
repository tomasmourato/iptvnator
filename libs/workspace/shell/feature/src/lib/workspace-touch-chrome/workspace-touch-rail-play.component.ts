import {
    ChangeDetectionStrategy,
    Component,
    input,
    ViewEncapsulation,
} from '@angular/core';
import type { PortalRailLink } from '@iptvnator/portal/shared/util';
import { WorkspaceTouchPlayComponent } from './workspace-touch-play.component';

/**
 * Compact landscape for a phone held sideways (fork-only, tv-shell touch
 * mode): the rail's Play, projected by the shell in place of the provider
 * region, so a source's sections take one rail slot instead of six. Deferred
 * by the shell; its stylesheet is global and scoped to the shell's
 * `data-touch-chrome='landscape'`, and turns the context panel into the
 * drawer phones use, so none of it loads elsewhere.
 */
@Component({
    selector: 'app-workspace-touch-rail-play',
    imports: [WorkspaceTouchPlayComponent],
    template: `<app-workspace-touch-play
        placement="beside"
        [primary]="primaryContextLinks()"
        [secondary]="secondaryContextLinks()"
        [currentSection]="currentSection()"
    />`,
    styleUrl: './workspace-touch-landscape.scss',
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspaceTouchRailPlayComponent {
    readonly primaryContextLinks = input<PortalRailLink[]>([]);
    readonly secondaryContextLinks = input<PortalRailLink[]>([]);
    readonly currentSection = input<string | null>(null);
}
