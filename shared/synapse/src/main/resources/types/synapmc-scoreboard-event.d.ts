/** Add beside the upstream JsMacros declarations. This event edits the displayed sidebar. */
declare namespace Events {
    interface ScoreboardText extends JavaObject {
        getString(): string;
        getJson(): string;
        getRaw(): JavaObject;
    }
    interface Scoreboard extends BaseEvent {
        /** Keep every previously displayed line. */
        cancel(): void;
        /** Keep selected previous lines; null means all. Line 0 is the title, 1–15 are rows. */
        cancel(lineOrList: number | number[] | Packages.java.util.List<number> | null): void;
        /** Read the pending displayed text, including edits/cancellation already applied. */
        getText(line: number): ScoreboardText | null;
        /** Replace the title or an existing row with a string, TextHelper, or native text component. */
        setText(line: number, text: string | JavaObject): void;
        /** Insert at rows 1–15, shifting later rows down; the sidebar remains limited to 15 rows. */
        addLine(line: number, text: string | JavaObject): void;
        /** Remove a row. Remaining rows shift up. */
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
