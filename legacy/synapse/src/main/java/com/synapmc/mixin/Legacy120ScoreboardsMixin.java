package com.synapmc.mixin;

import com.synapmc.Legacy120SidebarSession;
import net.minecraft.class_269;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xyz.wagyourtail.jsmacros.client.api.helpers.TextHelper;
import xyz.wagyourtail.jsmacros.client.api.helpers.screen.ScoreboardObjectiveHelper;
import xyz.wagyourtail.jsmacros.core.helpers.BaseHelper;

/** 1.20.1 keeps scores as ScoreboardPlayerScore instead of the newer entry record. */
@Mixin(targets = "xyz.wagyourtail.jsmacros.client.api.helpers.world.ScoreboardsHelper", remap = false)
public abstract class Legacy120ScoreboardsMixin extends BaseHelper<class_269> {
    protected Legacy120ScoreboardsMixin(class_269 board) { super(board); }
    @Shadow public abstract ScoreboardObjectiveHelper getObjectiveSlot(int slot);
    @Shadow public abstract ScoreboardObjectiveHelper getObjectiveForTeamColorIndex(int index);
    @Shadow public abstract int getPlayerTeamColorIndex();

    @Unique public TextHelper getText(int line) {
        if (line < 0 || line > 15) return null;
        ScoreboardObjectiveHelper objective = getObjectiveForTeamColorIndex(getPlayerTeamColorIndex());
        if (objective == null) objective = getObjectiveSlot(1);
        if (objective == null) return null;
        if (line == 0) return objective.getDisplayName();
        var rows = Legacy120SidebarSession.rows(base, objective.getRaw());
        return line > rows.size() ? null : TextHelper.wrap((net.minecraft.class_2561) rows.get(line - 1).text());
    }
}
