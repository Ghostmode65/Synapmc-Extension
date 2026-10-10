/** Requires synapmc-hud.d.ts alongside the upstream JsMacros editor declarations. */
declare namespace Events {
    interface renderTooltip extends BaseEvent {
        /** Final bounds of the item tooltip in this frame. The anchor cannot be moved. */
        readonly element: Packages.com.synapmc.LoreWindow;
        /** Queue one render element, a JavaScript/Lua array, or a Java list for this tooltip only. */
        draw(elementOrList: JavaObject | JavaObject[] | Packages.java.util.List<JavaObject>): void;
        joinable(): true;
    }
}

interface Events {
    renderTooltip: Events.renderTooltip;
}

declare namespace Packages.com.synapmc.event {
    interface EventRenderTooltip extends Events.renderTooltip {}
}
