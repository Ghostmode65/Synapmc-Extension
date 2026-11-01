
declare namespace Events {
    interface ScoreboardText extends JavaObject {
        getString(): string;
        getJson(): string;
        getRaw(): JavaObject;
    }
    interface Scoreboard extends BaseEvent {

        cancel(): void;

        cancel(lineOrList: number | number[] | Packages.java.util.List<number> | null): void;

        getText(line: number): ScoreboardText | null;

        getLines(): Packages.java.util.List<ScoreboardText>;

        setText(line: number, text: string | JavaObject): void;

        addLine(line: number, text: string | JavaObject): void;

        removeLine(line: number): void;
        joinable(): true;
    }
}

interface Events {
    Scoreboard: Events.Scoreboard;
}

declare namespace Packages.com.synapmc.event {
    interface EventScoreboard extends Events.Scoreboard {}
}
