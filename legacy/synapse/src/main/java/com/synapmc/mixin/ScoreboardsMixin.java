package com.synapmc.mixin;

import java.util.Comparator;
import java.util.List;
import net.minecraft.class_269;
import net.minecraft.class_268;
import net.minecraft.class_9011;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xyz.wagyourtail.jsmacros.client.api.helpers.TextHelper;
import xyz.wagyourtail.jsmacros.client.api.helpers.screen.ScoreboardObjectiveHelper;
import xyz.wagyourtail.jsmacros.core.helpers.BaseHelper;

/** Native 1.21/1.21.1 sidebar text access; getObjectiveSlot remains untouched. */
@Mixin(targets = "xyz.wagyourtail.jsmacros.client.api.helpers.world.ScoreboardsHelper", remap = false)
public abstract class ScoreboardsMixin extends BaseHelper<class_269> {
    protected ScoreboardsMixin(class_269 board) { super(board); }

    @Shadow public abstract ScoreboardObjectiveHelper getObjectiveSlot(int slot);
    @Shadow public abstract ScoreboardObjectiveHelper getObjectiveForTeamColorIndex(int index);
    @Shadow public abstract int getPlayerTeamColorIndex();

    @Unique public TextHelper getText(int line) {
        if (line < 0 || line > 15) return null;
        ScoreboardObjectiveHelper objective = getObjectiveForTeamColorIndex(getPlayerTeamColorIndex());
        if (objective == null) objective = getObjectiveSlot(1);
        if (objective == null) return null;
        if (line == 0) return objective.getDisplayName();

        List<class_9011> entries = base.method_1184(objective.getRaw()).stream()
            .filter(entry -> !entry.method_55385())
            .sorted(Comparator.comparingInt(class_9011::comp_2128).reversed()
                .thenComparing(class_9011::comp_2127, String.CASE_INSENSITIVE_ORDER))
            .limit(15).toList();
        if (line > entries.size()) return null;
        class_9011 entry = entries.get(line - 1);
        return TextHelper.wrap(class_268.method_1142(base.method_1164(entry.comp_2127()), entry.method_55387()));
    }
}
