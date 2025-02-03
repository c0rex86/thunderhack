package thunder.hack.features.modules.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.utility.render.Render2DEngine;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static thunder.hack.core.Managers.FRIEND;

public class PenisESP extends Module {
    private final Setting<Float> scale = new Setting<>("Scale", 0.5f, 0.1f, 2.0f);
    private final Setting<ColorSetting> color = new Setting<>("Color", new ColorSetting(new Color(0xFFE1BE, true)));
    private final Setting<ColorSetting> tipColor = new Setting<>("TipColor", new ColorSetting(new Color(0xFFB6C1, true)));
    private final Setting<Boolean> onlyOthers = new Setting<>("OnlyOthers", true);
    private final Setting<Boolean> friends = new Setting<>("Friends", true);
    private final Setting<Float> angle = new Setting<>("Angle", 0f, -45f, 45f);
    private final Setting<Boolean> guiPreview = new Setting<>("GuiPreview", true);
    private final Setting<Float> guiScale = new Setting<>("GuiScale", 1f, 0.5f, 3f);
    private final Setting<Boolean> soundEffects = new Setting<>("SoundEffects", true);
    
    public PenisESP() {
        super("PenisESP", Category.RENDER);
    }

    @Override
    public void onEnable() {
        if (mc.player != null && soundEffects.getValue()) {
            mc.player.getWorld().playSound(
                mc.player,
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.ENTITY_CHICKEN_EGG,
                SoundCategory.PLAYERS,
                1.0f, 1.0f
            );
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && soundEffects.getValue()) {
            mc.player.getWorld().playSound(
                mc.player,
                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.PLAYERS,
                0.5f, 1.5f
            );
        }
    }

    public void onRender2D(DrawContext context) {
        if (!guiPreview.getValue()) return;

        MatrixStack stack = context.getMatrices();
        stack.push();
        
        // Центрируем в окне настроек
        stack.translate(context.getScaledWindowWidth() / 2f + 60, context.getScaledWindowHeight() / 2f - 60, 0);
        
        // Масштабируем для GUI
        float s = guiScale.getValue() * 20;
        stack.scale(1, 1, 1);
        
        // Поворачиваем на заданный угол
        stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(angle.getValue()));
        
        // Рисуем ствол
        Render2DEngine.drawRect(stack, -4 * s, 0, 8 * s, 15 * s, color.getValue().getColorObject());
        
        // Рисуем головку
        Render2DEngine.drawRound(stack, -5 * s, 15 * s, 10 * s, 5 * s, 5 * s, tipColor.getValue().getColorObject());
        
        // Рисуем шарики
        Render2DEngine.drawRound(stack, -8 * s, -4 * s, 8 * s, 8 * s, 4 * s, color.getValue().getColorObject());
        Render2DEngine.drawRound(stack, 4 * s, -4 * s, 8 * s, 8 * s, 4 * s, color.getValue().getColorObject());
        
        stack.pop();
    }

    public void onRender3D(MatrixStack stack) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player && onlyOthers.getValue()) continue;
            if (!friends.getValue() && FRIEND.isFriend(player.getName().getString())) continue;

            Vec3d pos = player.getPos();
            double x = pos.x - mc.getEntityRenderDispatcher().camera.getPos().getX();
            double y = pos.y - mc.getEntityRenderDispatcher().camera.getPos().getY();
            double z = pos.z - mc.getEntityRenderDispatcher().camera.getPos().getZ();

            stack.push();
            stack.translate(x, y + 2.3, z);
            
            // Поворот на заданный угол
            stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(angle.getValue()));

            // Рисуем основание (ствол)
            float baseWidth = 0.12f * scale.getValue();
            float baseHeight = 0.4f * scale.getValue();
            Box shaft = new Box(-baseWidth, 0, -baseWidth,
                baseWidth, baseHeight, baseWidth);
            Render3DEngine.drawFilledBox(stack, shaft, color.getValue().getColorObject());

            // Рисуем головку
            stack.push();
            stack.translate(0, baseHeight, 0);
            float tipScale = 0.16f * scale.getValue();
            Box tip = new Box(-tipScale, 0, -tipScale,
                tipScale, tipScale * 1.2f, tipScale);
            Render3DEngine.drawFilledBox(stack, tip, tipColor.getValue().getColorObject());
            stack.pop();

            // Рисуем шарики (увеличенные и слегка опущенные)
            float ballScale = 0.14f * scale.getValue();
            stack.translate(-0.18f * scale.getValue(), -0.05f * scale.getValue(), 0);
            Render3DEngine.drawSphere(stack, ballScale, 20, 20, color.getValue().getColorObject().getRGB());
            
            stack.translate(0.36f * scale.getValue(), 0, 0);
            Render3DEngine.drawSphere(stack, ballScale, 20, 20, color.getValue().getColorObject().getRGB());

            stack.pop();
        }
    }
}
