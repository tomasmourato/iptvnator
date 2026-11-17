import { inject } from '@angular/core';
import { EpgRuntimeBridgeService } from '@iptvnator/epg/data-access';
import {
    RuntimeCapabilitiesService,
    ServerStorageStatusService,
} from '@iptvnator/services';
import { buildSettingsSectionNavItems } from './settings-options';
import type { SettingsSection } from './settings.models';

/**
 * The settings sections this runtime offers, in navigation order: the
 * settings page and the phone section list share it. The EPG page shows
 * wherever a guide exists (XMLTV import or a portal's own guide). Call it
 * in an injection context.
 */
export function injectSettingsSectionNav(): SettingsSection[] {
    const runtime = inject(RuntimeCapabilitiesService);
    const epg = inject(EpgRuntimeBridgeService);
    return buildSettingsSectionNavItems({
        supportsEpg:
            (epg.supportsImport && epg.supportsDataManagement) ||
            runtime.supportsEpg ||
            runtime.supportsProviderEpg,
        supportsRemoteControl: runtime.supportsRemoteControl,
        supportsServerStorage: inject(ServerStorageStatusService).isActive(),
    });
}
