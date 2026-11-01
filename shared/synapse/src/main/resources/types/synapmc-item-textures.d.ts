
declare namespace Packages.com.synapmc {
    interface ItemTexture extends JavaObject {

        getIdentifiers(): Packages.java.util.List<string>;
        export(): Packages.java.util.List<Packages.java.io.File>;

        replace(pngFile: string | JavaObject, mcmetaFile?: string | JavaObject | null): ItemTexture;
        revert(): ItemTexture;
    }
    interface ItemStackTexture extends JavaObject {

        getIdentifiers(): Packages.java.util.List<string>;

        export(): Packages.java.util.List<Packages.java.io.File>;

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
