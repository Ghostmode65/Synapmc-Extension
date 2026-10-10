/** Add this file beside your JsMacros TypeScript declarations for editor autocomplete. */
declare namespace Client {
    function getOS(): "windows" | "linux" | "mac" | "unknown";
    function jsVersion(): "legacy" | "community" | "reload";
    function isFullscreen(): boolean;
    function toggleFullscreen(enabled: boolean): boolean;
}

declare namespace Packages.xyz.wagyourtail.jsmacros.client.api.library.impl {
    interface FClient {
        getOS(): "windows" | "linux" | "mac" | "unknown";
        jsVersion(): "legacy" | "community" | "reload";
        isFullscreen(): boolean;
        toggleFullscreen(enabled: boolean): boolean;
    }
}

declare namespace Packages.com.jsmacrosce.jsmacros.client.api.library.impl {
    interface FClient {
        getOS(): "windows" | "linux" | "mac" | "unknown";
        jsVersion(): "legacy" | "community" | "reload";
        isFullscreen(): boolean;
        toggleFullscreen(enabled: boolean): boolean;
    }
}
