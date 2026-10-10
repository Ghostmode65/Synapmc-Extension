/** Add beside the upstream JsMacros declarations. Supported on Legacy 1.21/1.21.1 and current Community/Reloaded targets. */
declare namespace Packages.com.synapmc {
    interface ItemTexture extends JavaObject {
        /** Distinct PNGs from the item's original default GUI model. */
        getIdentifiers(): Packages.java.util.List<string>;
        export(): Packages.java.util.List<Packages.java.io.File>;
        /** Replace its main PNG in normal native item rendering; custom-model stacks are excluded. Reloads resources asynchronously. */
        replace(pngFile: string | JavaObject, mcmetaFile?: string | JavaObject | null): ItemTexture;
        revert(): ItemTexture;
    }
    interface ItemStackTexture extends JavaObject {
        /** Distinct PNG resources used by the stack's current GUI model. May be empty. */
        getIdentifiers(): Packages.java.util.List<string>;
        /** Copies active PNGs and available .png.mcmeta files to .jsmacros/assets/textures/assets/<namespace>/. */
        export(): Packages.java.util.List<Packages.java.io.File>;
        /** Privately copies this helper's stack. Replacement applies to JsMacros GUI item elements. */
        replace(pngFile: string | JavaObject, mcmetaFile?: string | JavaObject | null): ItemStackTexture;
        revert(): ItemStackTexture;
    }
}
declare namespace Packages.xyz.wagyourtail.jsmacros.client.api.helpers.inventory {
    interface ItemHelper { getTexture(): Packages.com.synapmc.ItemTexture; }
    interface ItemStackHelper { getTexture(): Packages.com.synapmc.ItemStackTexture; }
}
declare namespace Packages.xyz.wagyourtail.jsmacros.client.api.helper.inventory {
    interface ItemHelper { getTexture(): Packages.com.synapmc.ItemTexture; }
    interface ItemStackHelper { getTexture(): Packages.com.synapmc.ItemStackTexture; }
}
declare namespace Packages.com.jsmacrosce.jsmacros.client.api.helper.inventory {
    interface ItemHelper { getTexture(): Packages.com.synapmc.ItemTexture; }
    interface ItemStackHelper { getTexture(): Packages.com.synapmc.ItemStackTexture; }
}
