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

public class WallBuilder extends Module {
    private final Setting<Mode> mode = new Setting<>("Mode", Mode.Simple);
    private final Setting<Integer> length = new Setting<>("Length", 10, 1, 50);
    private final Setting<Integer> height = new Setting<>("Height", 3, 1, 10);
    private final Setting<Integer> thickness = new Setting<>("Thickness", 1, 1, 5);
    private final Setting<Boolean> pattern = new Setting<>("Pattern", false);
    private final Setting<Boolean> foundation = new Setting<>("Foundation", true);
    private final Setting<Boolean> autoSwitch = new Setting<>("AutoSwitch", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<Color> placeColor = new Setting<>("PlaceColor", new Color(0, 255, 0, 100), v -> render.getValue());
    
    private final Set<BlockPos> placePositions = new HashSet<>();
    private int currentSlot = -1;
    private BlockPos startPos = null;
    private Direction wallDirection = null;
    
    public WallBuilder() {
        super("WallBuilder", Category.MISC);
    }

    public enum Mode {
        Simple,    // Простая стена
        Castle,    // С зубцами наверху
        Fortified, // С укреплениями
        Decorative // С узорами
    }

    @Override
    public void onEnable() {
        startPos = mc.player.getBlockPos();
        wallDirection = mc.player.getHorizontalFacing();
        calculatePositions();
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck() || startPos == null) return;
        
        // Обновляем позиции если нужно
        if (placePositions.isEmpty()) {
            startPos = mc.player.getBlockPos();
            calculatePositions();
        }

        // Проверяем наличие блоков
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
        
        int l = length.getValue();
        int h = height.getValue();
        int t = thickness.getValue();
        
        // Получаем направление стены и перпендикулярное ему
        Direction perpendicular = wallDirection.getOpposite();
        
        // Основание стены
        if (foundation.getValue()) {
            for (int x = 0; x < l; x++) {
                for (int z = 0; z < t; z++) {
                    BlockPos basePos = startPos.offset(wallDirection, x).offset(perpendicular, z);
                    placePositions.add(basePos.down());
                }
            }
        }
        
        // Строим основную часть стены
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < l; x++) {
                for (int z = 0; z < t; z++) {
                    BlockPos pos = startPos.offset(wallDirection, x).offset(perpendicular, z).up(y);
                    
                    // Добавляем узоры если включено
                    if (pattern.getValue() && shouldAddPattern(x, y, z)) {
                        placePositions.add(pos);
                        continue;
                    }
                    
                    // Добавляем блоки в зависимости от режима
                    switch (mode.getValue()) {
                        case Simple:
                            placePositions.add(pos);
                            break;
                            
                        case Castle:
                            if (y == h - 1 && x % 2 == 0) {
                                placePositions.add(pos.up());
                            }
                            placePositions.add(pos);
                            break;
                            
                        case Fortified:
                            if (x % 4 == 0 && y == h - 1) {
                                // Добавляем укрепления каждые 4 блока
                                placePositions.add(pos.up());
                                placePositions.add(pos.up().offset(perpendicular));
                            }
                            placePositions.add(pos);
                            break;
                            
                        case Decorative:
                            // Добавляем декоративные элементы
                            if (y % 2 == 0 && x % 3 == 0) {
                                placePositions.add(pos.offset(perpendicular));
                            }
                            placePositions.add(pos);
                            break;
                    }
                }
            }
        }
    }

    private boolean shouldAddPattern(int x, int y, int z) {
        // Создаем разные узоры в зависимости от координат
        return (y % 2 == 0 && x % 2 == 0) || // Шахматный узор
               (y % 3 == 0 && x % 3 == 0) || // Большой шахматный узор
               (y == height.getValue() - 1 && x % 2 == 0); // Зубцы наверху
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
        return mode.getValue().toString() + " " + placePositions.size();
    }
} 