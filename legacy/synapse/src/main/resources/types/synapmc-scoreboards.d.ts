/** Available only with Legacy SynapMc on Minecraft 1.21 or 1.21.1. */
declare namespace Packages.xyz.wagyourtail.jsmacros.client.api.helpers.world {
    interface ScoreboardsHelper {
        /** Sidebar title at 0; visible rows from 1 to 15, top to bottom. */
        getText(line: number): Packages.xyz.wagyourtail.jsmacros.client.api.helpers.TextHelper | null;
    }
}
