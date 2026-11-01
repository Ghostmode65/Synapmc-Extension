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

declare namespace Events {
    interface Tooltip extends BaseEvent {

        readonly element: Packages.com.synapmc.LoreWindow;
        getX(): number;
        getY(): number;
        getWidth(): number;
        getHeight(): number;

        draw(elementOrList: JavaObject | JavaObject[] | Packages.java.util.List<JavaObject>): void;
        setOnInt(draw2d: JavaObject): void;
        joinable(): true;
    }
}

interface Events {
    Tooltip: Events.Tooltip;
}

declare namespace Packages.com.synapmc.event {
    interface EventTooltip extends Events.Tooltip {}
}
