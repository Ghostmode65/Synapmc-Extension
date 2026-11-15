/** Detached click-event fields in a Java list; null when no click events exist. */
interface SynapMcClickEvent {
    get(name: "action"): string;
    get(name: "command" | "url" | "path" | "value" | "id"): string | null;
    get(name: "page"): number | string | null;
    get(name: string): unknown;
}

interface SynapMcClickEventList {
    size(): number;
    get(index: number): SynapMcClickEvent;
}

declare namespace Packages.xyz.wagyourtail.jsmacros.client.api.helpers {
    interface TextHelper { getClickEvent(): SynapMcClickEventList | null; }
}
declare namespace Packages.xyz.wagyourtail.jsmacros.client.api.helper {
    interface TextHelper { getClickEvent(): SynapMcClickEventList | null; }
}
declare namespace Packages.com.jsmacrosce.jsmacros.client.api.helper {
    interface TextHelper { getClickEvent(): SynapMcClickEventList | null; }
}
