package com.synapmc.mixin;

import com.synapmc.EditorColors;
import com.synapmc.EditorSnippets;
import @CORE_PACKAGE@.client.gui.editor.highlighting.AbstractRenderCodeCompiler;
import @CORE_PACKAGE@.client.gui.editor.highlighting.AutoCompleteSuggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "@CORE_PACKAGE@.client.gui.screens.EditorScreen", remap = false)
public abstract class EditorColorMixin {
    @Unique private final EditorColors synapmc$colors = new EditorColors();

    @Inject(method = "@ELEMENT_RENDER_METHOD@(L@EDITOR_CONTEXT_INTERNAL@;IIF)V", at = @At(value = "INVOKE",
            target = "L@CORE_INTERNAL@/client/gui/editor/highlighting/AbstractRenderCodeCompiler;getRenderedText()[L@TEXT_INTERNAL@;"), require = @EDITOR_REQUIRE@, remap = false)
    private void synapmc$highlightColors(@EDITOR_CONTEXT@ context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        synapmc$colors.render(this, context);
    }

    @Redirect(method = "openRClickMenu", at = @At(value = "INVOKE",
            target = "L@CORE_INTERNAL@/client/gui/editor/highlighting/AbstractRenderCodeCompiler;getRightClickOptions(I)Ljava/util/Map;"), remap = false)
    private Map<String, Runnable> synapmc$colorOption(AbstractRenderCodeCompiler compiler, int index) {
        return EditorSnippets.options(this, index, EditorColors.options(this, index, compiler.getRightClickOptions(index)));
    }

    @Redirect(method = "compileRenderedText", at = @At(value = "INVOKE",
            target = "L@CORE_INTERNAL@/client/gui/editor/highlighting/AbstractRenderCodeCompiler;getSuggestions()Ljava/util/List;"), remap = false)
    private List<AutoCompleteSuggestion> synapmc$snippetSuggestions(AbstractRenderCodeCompiler compiler) {
        var markers = EditorSnippets.completions(this);
        if (markers.isEmpty()) return compiler.getSuggestions();
        List<AutoCompleteSuggestion> suggestions = new ArrayList<>();
        for (var marker : markers) suggestions.add(new AutoCompleteSuggestion(marker.start(), marker.text()));
        return suggestions;
    }
}
