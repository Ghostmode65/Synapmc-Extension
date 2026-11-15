package com.synapmc.mixin;

import com.synapmc.TextClickEvents;
import java.util.Map;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "@CORE_PACKAGE@.client.api.@HELPER_PACKAGE@.TextHelper", remap = false)
public abstract class TextHelperMixin {
    @Shadow public abstract String getJson();

    @Unique public List<Map<String, Object>> getClickEvent() {
        return TextClickEvents.fromJson(getJson());
    }
}
