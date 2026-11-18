import {
    ChangeDetectionStrategy,
    Component,
    computed,
    input,
    ViewEncapsulation,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { RouterLink, RouterLinkActive } from '@angular/router';
import type { PortalRailLink } from '@iptvnator/portal/shared/util';
import { WorkspaceTouchPlayComponent } from './workspace-touch-play.component';

const HOME = '/workspace/dashboard';
const SOURCES = '/workspace/sources';
const FAVORITES = '/workspace/global-favorites';
const RECENT = '/workspace/global-recent';

/** The rail's own icon for Sources (workspace-shell-rail-links). */
const ICON_OVERRIDES: Readonly<Record<string, string>> = {
    [SOURCES]: 'playlist_play',
};

/**
 * Bottom navigation for a phone in portrait (fork-only, tv-shell touch
 * mode): Home · Sources · Play · Favorites · Recent. It replaces the
 * sideways-scrolling top bar; Settings moves to a gear in the header and a
 * source's sections into Play's dial. Deferred by the shell, so its styles
 * (global, scoped to the shell's `data-touch-chrome`) never load elsewhere.
 */
@Component({
    selector: 'app-workspace-touch-bottom-bar',
    imports: [
        MatIcon,
        NgTemplateOutlet,
        RouterLink,
        RouterLinkActive,
        WorkspaceTouchPlayComponent,
    ],
    templateUrl: './workspace-touch-bottom-bar.component.html',
    // The header's touch search rides along: same deferred chunk, same
    // global scope.
    styleUrls: [
        './workspace-touch-bottom-bar.component.scss',
        './workspace-touch-search.scss',
    ],
    encapsulation: ViewEncapsulation.None,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspaceTouchBottomBarComponent {
    readonly workspaceLinks = input<PortalRailLink[]>([]);
    readonly primaryContextLinks = input<PortalRailLink[]>([]);
    readonly secondaryContextLinks = input<PortalRailLink[]>([]);
    readonly currentSection = input<string | null>(null);

    private readonly linkAt = (path: string) => {
        const link = this.workspaceLinks().find(
            (candidate) => candidate.path.join('/') === path
        );
        return link
            ? { ...link, icon: ICON_OVERRIDES[path] ?? link.icon }
            : null;
    };

    /** Home (when the dashboard is on) and Sources, left of Play. */
    readonly before = computed(() =>
        [this.linkAt(HOME), this.linkAt(SOURCES)].filter(
            (link): link is PortalRailLink => link !== null
        )
    );
    /** Favorites and Recent, right of Play. */
    readonly after = computed(() =>
        [this.linkAt(FAVORITES), this.linkAt(RECENT)].filter(
            (link): link is PortalRailLink => link !== null
        )
    );
}
