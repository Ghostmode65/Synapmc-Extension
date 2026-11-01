package xyz.wagyourtail.jsmacros.core.event;


public class BaseEvent {
    protected boolean cancelled;
    public boolean joinable() { return false; }
    public final void cancel() { cancelled = true; }
    public final boolean isCanceled() { return cancelled; }
}
