package xyz.wagyourtail.jsmacros.client.api.helpers.screen;

import net.minecraft.class_266;
import xyz.wagyourtail.jsmacros.core.helpers.BaseHelper;
import xyz.wagyourtail.jsmacros.client.api.helpers.TextHelper;


public abstract class ScoreboardObjectiveHelper extends BaseHelper<class_266> {
    protected ScoreboardObjectiveHelper(class_266 objective) { super(objective); }
    public abstract TextHelper getDisplayName();
}
