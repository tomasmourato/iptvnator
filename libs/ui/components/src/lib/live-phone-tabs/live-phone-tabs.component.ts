import {
    ChangeDetectionStrategy,
    Component,
    output,
    signal,
} from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
// File-level entry: the barrel is in the startup bundle.
import { injectPhoneLayout } from '../phone-layout/phone-layout';

export type LivePhonePane = 'channels' | 'guide';

/**
 * Phone tabs under a playing live channel: the channel list or the
 * programme guide, one at a time below the player. The live layout reads
 * the chosen pane from the host's `data-pane` (live-layout mixin, phone
 * rules). Above the phone breakpoint it renders nothing.
 */
@Component({
    selector: 'app-live-phone-tabs',
    imports: [TranslatePipe],
    templateUrl: './live-phone-tabs.component.html',
    styleUrl: './live-phone-tabs.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[attr.data-pane]': 'pane()' },
})
export class LivePhoneTabsComponent {
    readonly isPhone = injectPhoneLayout();
    readonly pane = signal<LivePhonePane>('channels');
    /** The guide tab was chosen: the layout expands a collapsed guide. */
    readonly guideSelected = output<void>();

    select(pane: LivePhonePane): void {
        this.pane.set(pane);
        if (pane === 'guide') {
            this.guideSelected.emit();
        }
    }
}
