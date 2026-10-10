/** Add beside the upstream JsMacros declarations. Coordinates are scaled GUI pixels. */
declare namespace Packages.com.synapmc {
    interface LoreWindow extends JavaObject {
        getX(): number;
        getY(): number;
        getWidth(): number;
        getHeight(): number;
        getScaledLeft(): number;
        getScaledTop(): number;
        getScaledRight(): number;
        getScaledBottom(): number;
        getScaledWidth(): number;
        getScaledHeight(): number;
        getParentWidth(): number;
        getParentHeight(): number;
        /** Read-only target: positioning/alignment mutators throw UnsupportedOperationException. */
        moveTo(x: number, y: number): LoreWindow;
        moveToX(x: number): LoreWindow;
        moveToY(y: number): LoreWindow;
        alignHorizontally(alignment: string, offset?: number): LoreWindow;
        alignHorizontally(other: LoreWindow, alignment: string, offset?: number): LoreWindow;
        alignVertically(alignment: string, offset?: number): LoreWindow;
        alignVertically(other: LoreWindow, alignment: string, offset?: number): LoreWindow;
        align(horizontal: string, vertical: string): LoreWindow;
        align(horizontal: string, horizontalOffset: number, vertical: string, verticalOffset: number): LoreWindow;
        align(other: LoreWindow, horizontal: string, vertical: string): LoreWindow;
        align(other: LoreWindow, horizontal: string, horizontalOffset: number, vertical: string, verticalOffset: number): LoreWindow;
    }
}

declare namespace Hud {
    /** Full item tooltip with background padding, or null (nil in Lua) when absent/unsupported. */
    function getLoreWindow(): Packages.com.synapmc.LoreWindow | null;
}

declare namespace Packages.xyz.wagyourtail.jsmacros.client.api.library.impl {
    interface FHud { getLoreWindow(): Packages.com.synapmc.LoreWindow | null; }
}
declare namespace Packages.com.jsmacrosce.jsmacros.client.api.library.impl {
    interface FHud { getLoreWindow(): Packages.com.synapmc.LoreWindow | null; }
}
