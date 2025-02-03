package thunder.hack.injection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thunder.hack.utility.player.InventoryUtility;
import net.minecraft.client.gui.screen.Screen;
import thunder.hack.core.Managers;
import net.minecraft.client.MinecraftClient;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen extends Screen {
    private ButtonWidget dropAllButton;
    private int currentSlot = 9;
    private long lastDropTime = 0;
    private boolean isDropping = false;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    protected MixinInventoryScreen() {
        super(Text.of(""));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void injectInit(CallbackInfo ci) {
        dropAllButton = this.addDrawableChild(ButtonWidget.builder(Text.of("Выбросить всё"), button -> {
            if (!isDropping) {
                isDropping = true;
                currentSlot = 9;
                lastDropTime = 0;
            }
        })
        .dimensions(5, 5, 100, 20)
        .build());
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (isDropping && mc.player != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDropTime >= 50) { // Уменьшаем задержку до 50мс
                lastDropTime = currentTime;
                int droppedStacks = 0;
                
                while (currentSlot < 45 && droppedStacks < 1) { // Выбрасываем 1 стак за раз
                    if (!mc.player.getInventory().getStack(currentSlot).isEmpty()) {
                        InventoryUtility.dropStack(currentSlot); // Используем dropStack вместо drop
                        droppedStacks++;
                    }
                    currentSlot++;
                }

                if (currentSlot >= 45) {
                    isDropping = false;
                }
            }
        }
    }
} 