import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';
import { WorkspaceShellSearchScopeService } from '../../services/workspace-shell-search-scope.service';

/**
 * Phone-only row under the header while a search runs inside a category:
 * the way back out of the category, which the header's scope chip has no
 * room to offer at phone width. The shell creates it only at the phone
 * breakpoint, so wider screens never load its stylesheet.
 */
@Component({
    selector: 'app-workspace-shell-search-scope',
    imports: [MatIconModule, TranslatePipe],
    templateUrl: './workspace-shell-search-scope.component.html',
    styleUrl: './workspace-shell-search-scope.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspaceShellSearchScopeComponent {
    readonly scope = inject(WorkspaceShellSearchScopeService);
}
