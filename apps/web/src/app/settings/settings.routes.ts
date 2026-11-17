import { Routes } from '@angular/router';
import { PHONE_LAYOUT_QUERY } from '@iptvnator/ui/components/phone-layout';
import { settingsUnsavedChangesGuard } from './settings-unsaved-changes.guard';

/** A phone opens settings on the section list; wider screens on General. */
const onPhone = () =>
    typeof window.matchMedia === 'function' &&
    window.matchMedia(PHONE_LAYOUT_QUERY).matches;

/** `/workspace/settings`, loaded with the settings page. */
export const SETTINGS_ROUTES: Routes = [
    {
        path: '',
        pathMatch: 'full',
        canMatch: [onPhone],
        loadComponent: () =>
            import('./settings-section-index.component').then(
                (c) => c.SettingsSectionIndexComponent
            ),
    },
    {
        path: '',
        pathMatch: 'full',
        redirectTo: 'general',
    },
    {
        // One routed component for every section: the same
        // instance survives :section param changes (default
        // route reuse), so the settings form — and its dirty
        // state — persists while the user moves between
        // section pages. The guard only intercepts leaving
        // the settings area with unsaved edits.
        path: ':section',
        canDeactivate: [settingsUnsavedChangesGuard],
        loadComponent: () =>
            import('./settings.component').then((c) => c.SettingsComponent),
    },
];
