import {
    ChangeDetectionStrategy,
    Component,
    computed,
    inject,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { SettingsStore } from '@iptvnator/services';
import { SETTINGS_NAV_GROUPS } from '@iptvnator/workspace/shell/util/settings-search';
import {
    SETTINGS_EMBEDDED_PLAYER_OPTIONS,
    SETTINGS_OS_PLAYER_OPTIONS,
} from './settings-options';
import { injectSettingsSectionNav } from './settings-section-nav';

const PLAYER_LABEL_KEYS = new Map<string, string>(
    [...SETTINGS_EMBEDDED_PLAYER_OPTIONS, ...SETTINGS_OS_PLAYER_OPTIONS].map(
        (option) => [option.id, option.labelKey]
    )
);

/**
 * The first settings screen on a phone: every section in one list, the
 * standard mobile settings pattern, instead of opening General with the
 * sections hidden in the drawer. Playback states the default video player
 * right in the list. Sections open as history entries, so Back returns here.
 * Routed only at the phone breakpoint (see settings.routes.ts).
 */
@Component({
    selector: 'app-settings-section-index',
    imports: [MatIconModule, NgTemplateOutlet, RouterLink, TranslatePipe],
    templateUrl: './settings-section-index.component.html',
    styleUrl: './settings-section-index.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsSectionIndexComponent {
    private readonly settingsStore = inject(SettingsStore);
    private readonly sections = injectSettingsSectionNav().filter(
        (section) => section.visible
    );

    readonly groups = SETTINGS_NAV_GROUPS.map((group) => ({
        ...group,
        sections: this.sections.filter((section) => section.group === group.id),
    })).filter((group) => group.sections.length > 0);
    readonly footer = this.sections.filter((section) => !section.group);

    /** The default player's label, shown on the Playback row. */
    readonly playerLabelKey = computed(
        () => PLAYER_LABEL_KEYS.get(this.settingsStore.player()) ?? null
    );
}
