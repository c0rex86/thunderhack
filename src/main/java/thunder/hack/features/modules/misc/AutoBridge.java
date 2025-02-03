package thunder.hack.features.modules.misc;

import net.minecraft.block.Block;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.render.Render3DEngine;

import java.awt.*;
import java.util.*;

public class AutoBridge extends Module {
    private final Setting<Integer> length = new Setting<>("Length", 10, 1, 50);
    private final Setting<Integer> width = new Setting<>("Width", 1, 1, 3);
    private final Setting<Boolean> rails = new Setting<>("Rails", false);
    private final Setting<Boolean> diagonal = new Setting<>("Diagonal", false);
    private final Setting<Boolean> autoWalk = new Setting<>("AutoWalk", true);
    private final Setting<Boolean> safewalk = new Setting<>("Safewalk", true);
    private final Setting<Boolean> sprint = new Setting<>("Sprint", false);
    private final Setting<Boolean> autoSwitch = new Setting<>("AutoSwitch", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<Color> placeColor = new Setting<>("PlaceColor", new Color(0, 255, 0, 100), v -> render.getValue());
    
    private final Set<BlockPos> placePositions = new HashSet<>();
    private int currentSlot = -1;
    private Direction bridgeDirection = null;
    private BlockPos startPos = null;
    
    public AutoBridge() {
        super("AutoBridge", Category.MISC);
    }

    @Override
    public void onEnable() {
        startPos = mc.player.getBlockPos();
        bridgeDirection = mc.player.getHorizontalFacing();
        calculatePositions();
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck() || startPos == null) return;
        
        // Обновляем позиции если нужно
        if (placePositions.isEmpty() || !mc.player.getBlockPos().equals(startPos)) {
            startPos = mc.player.getBlockPos();
            calculatePositions();
        }

        // Проверяем наличие блоков
        if (!hasBlocks()) return;

        // Включаем безопасную ходьбу
        if (safewalk.getValue()) {
            mc.player.setSprinting(false);
        }

        // Автоматическая ходьба
        if (autoWalk.getValue()) {
            mc.options.forwardKey.setPressed(true);
            if (sprint.getValue() && !safewalk.getValue()) {
                mc.player.setSprinting(true);
            }
        }

        // Размещаем блоки
        Iterator<BlockPos> it = placePositions.iterator();
        int placed = 0;
        while (it.hasNext() && placed < 4) { // Максимум 4 блока за тик
            BlockPos pos = it.next();
            if (canPlace(pos)) {
                placeBlock(pos);
                placed++;
            }
            it.remove();
        }

        // Возвращаем слот
        if (currentSlot != -1 && autoSwitch.getValue()) {
            mc.player.getInventory().selectedSlot = currentSlot;
            currentSlot = -1;
        }
    }

    @Override
    public void onDisable() {
        if (autoWalk.getValue()) {
            mc.options.forwardKey.setPressed(false);
        }
    }

    private void calculatePositions() {
        placePositions.clear();
        
        int w = width.getValue();
        int l = length.getValue();
        
        // Рассчитываем смещения в зависимости от направления
        int dx = bridgeDirection.getOffsetX();
        int dz = bridgeDirection.getOffsetZ();
        
        // Для диагонального моста
        if (diagonal.getValue()) {
            dx = bridgeDirection.getOffsetX() + (bridgeDirection.rotateClockwise(Direction.Axis.Y).getOffsetX());
            dz = bridgeDirection.getOffsetZ() + (bridgeDirection.rotateClockwise(Direction.Axis.Y).getOffsetZ());
        }
        
        // Генерируем позиции для моста
        for (int i = 0; i < l; i++) {
            for (int j = -(w/2); j <= w/2; j++) {
                BlockPos pos;
                if (diagonal.getValue()) {
                    pos = startPos.add(dx * i + j * bridgeDirection.rotateClockwise(Direction.Axis.Y).getOffsetX(),
                                     -1,
                                     dz * i + j * bridgeDirection.rotateClockwise(Direction.Axis.Y).getOffsetZ());
                } else {
                    pos = startPos.add(dx * i,
                                     -1,
                                     dz * i).add(bridgeDirection.rotateClockwise(Direction.Axis.Y).getOffsetX() * j,
                                               0,
                                               bridgeDirection.rotateClockwise(Direction.Axis.Y).getOffsetZ() * j);
                }
                placePositions.add(pos);
                
                // Добавляем перила
                if (rails.getValue() && j == -(w/2) || j == w/2) {
                    placePositions.add(pos.up());
                }
            }
        }
    }

    private boolean hasBlocks() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem)
                return true;
        }
        return false;
    }

    private void switchToBlocks() {
        if (!autoSwitch.getValue()) return;
        
        currentSlot = mc.player.getInventory().selectedSlot;
        
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof BlockItem) {
                mc.player.getInventory().selectedSlot = i;
                break;
            }
        }
    }

    private boolean canPlace(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir();
    }

    private void placeBlock(BlockPos pos) {
        if (mc.interactionManager == null) return;
        
        switchToBlocks();
        
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }

    @Override
    public void onRender3D(MatrixStack stack) {
        if (!render.getValue() || fullNullCheck()) return;
        
        for (BlockPos pos : placePositions) {
            Box box = new Box(pos);
            Render3DEngine.drawFilledBox(stack, box, placeColor.getValue());
        }
    }
    
    @Override
    public String getDisplayInfo() {
        return placePositions.size() + " " + bridgeDirection.toString();
    }
} 