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

public class BuildHelper extends Module {
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Circle);
    private final Setting<Integer> radius = new Setting<>("Radius", 3, 1, 10);
    private final Setting<Integer> height = new Setting<>("Height", 1, 1, 10);
    private final Setting<Boolean> hollow = new Setting<>("Hollow", false);
    private final Setting<Boolean> autoSwitch = new Setting<>("AutoSwitch", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<Color> placeColor = new Setting<>("PlaceColor", new Color(0, 255, 0, 100), v -> render.getValue());
    
    private final Set<BlockPos> placePositions = new HashSet<>();
    private int currentSlot = -1;
    private BlockPos basePos = null;
    
    public BuildHelper() {
        super("BuildHelper", Category.MISC);
    }

    public enum Mode {
        Circle,
        Square,
        Pyramid,
        Sphere,
        Tower
    }

    @Override
    public void onEnable() {
        basePos = mc.player.getBlockPos();
        calculatePositions();
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck() || basePos == null) return;
        
        if (placePositions.isEmpty()) {
            basePos = mc.player.getBlockPos();
            calculatePositions();
        }

        // Проверяем наличие блоков в хотбаре
        if (!hasBlocks()) return;

        // Размещаем блоки
        Iterator<BlockPos> it = placePositions.iterator();
        int placed = 0;
        while (it.hasNext() && placed < 5) { // Максимум 5 блоков за тик
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

    private void calculatePositions() {
        placePositions.clear();
        
        switch (mode.getValue()) {
            case Circle:
                generateCircle();
                break;
            case Square:
                generateSquare();
                break;
            case Pyramid:
                generatePyramid();
                break;
            case Sphere:
                generateSphere();
                break;
            case Tower:
                generateTower();
                break;
        }
    }

    private void generateCircle() {
        int r = radius.getValue();
        for (int y = 0; y < height.getValue(); y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        if (!hollow.getValue() || x * x + z * z >= (r-1) * (r-1)) {
                            placePositions.add(basePos.add(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private void generateSquare() {
        int r = radius.getValue();
        for (int y = 0; y < height.getValue(); y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (!hollow.getValue() || Math.abs(x) == r || Math.abs(z) == r) {
                        placePositions.add(basePos.add(x, y, z));
                    }
                }
            }
        }
    }

    private void generatePyramid() {
        for (int y = 0; y < height.getValue(); y++) {
            int layerRadius = radius.getValue() - y;
            if (layerRadius < 0) break;
            
            for (int x = -layerRadius; x <= layerRadius; x++) {
                for (int z = -layerRadius; z <= layerRadius; z++) {
                    if (!hollow.getValue() || y == 0 || layerRadius == Math.max(Math.abs(x), Math.abs(z))) {
                        placePositions.add(basePos.add(x, y, z));
                    }
                }
            }
        }
    }

    private void generateSphere() {
        int r = radius.getValue();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z <= r * r) {
                        if (!hollow.getValue() || x * x + y * y + z * z >= (r-1) * (r-1)) {
                            placePositions.add(basePos.add(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private void generateTower() {
        int r = radius.getValue();
        for (int y = 0; y < height.getValue(); y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (!hollow.getValue() || Math.abs(x) == r || Math.abs(z) == r) {
                        placePositions.add(basePos.add(x, y, z));
                    }
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
        return mc.world.getBlockState(pos).getMaterial().isReplaceable();
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
        return mode.getValue().toString() + " " + placePositions.size();
    }
} 