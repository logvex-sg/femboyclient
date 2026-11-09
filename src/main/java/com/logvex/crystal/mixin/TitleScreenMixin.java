package com.logvex.crystal.mixin;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.compat.Compat;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla title-screen splash with a cute pastel one while the theme's
 * "splash text" option is on.
 *
 * <p>Vanilla builds its splash in {@code TitleScreen.init()}, so injecting at the tail is
 * the smallest possible change: the whole splash system (rotation, wobble, rendering) is
 * reused and only the string is swapped. With the option off nothing is touched, so the
 * default splash is preserved.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    /** The vanilla splash field, private; accessed through the mixin shadow. */
    @Shadow
    private SplashTextRenderer splashText;

    private static final String[] CUTE_SPLASHES = {
            "pink crystal, soft heart \u2661",
            "kawaii anarchy!",
            "crystals & cat ears \u2661",
            "stay cute, hit hard \u2726",
            "pastel pvp princess",
            "nuzzle the crystal \u2661",
            "purr-fectly lethal \u2726"
    };

    @Inject(method = "init", at = @At("TAIL"))
    private void crystal$cuteSplash(CallbackInfo info) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null || !bootstrap.theme().settings().splashText) {
            return;
        }
        int index = (int) ((System.currentTimeMillis() / 1000L) % CUTE_SPLASHES.length);
        this.splashText = new SplashTextRenderer(Compat.splashText(Text.literal(CUTE_SPLASHES[index])));
    }
}
