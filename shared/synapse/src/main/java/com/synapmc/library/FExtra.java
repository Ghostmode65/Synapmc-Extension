package com.synapmc.library;

import com.synapmc.ScoreboardSpoof;

public final class FExtra {
    public final FTexture texture = new FTexture();

    public ScoreboardSpoof.Handle scoreboard(int line, String text) {
        return ScoreboardSpoof.change(line, text);
    }
}
