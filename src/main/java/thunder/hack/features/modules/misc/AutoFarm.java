package thunder.hack.features.modules.misc;

import net.minecraft.block.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.render.Render3DEngine;
import net.minecraft.block.Blocks;

import java.awt.*;
import java.util.*;

public class AutoFarm extends Module {
    private final Setting<Integer> radius = new Setting<>("Radius", 3, 1, 5);
    private final Setting<Integer> actionsPerTick = new Setting<>("ActionsPerTick", 4, 1, 10);
    private final Setting<Integer> maxBoneMealTries = new Setting<>("MaxBoneMealTries", 3, 1, 10);
    private final Setting<Boolean> debug = new Setting<>("Debug", false);
    private final Setting<Boolean> tillGround = new Setting<>("TillGround", true);
    private final Setting<Boolean> harvestCrops = new Setting<>("HarvestCrops", true);
    private final Setting<Boolean> replantCrops = new Setting<>("ReplantCrops", true);
    private final Setting<Boolean> useBoneMeal = new Setting<>("UseBoneMeal", true);
    private final Setting<Boolean> autoSwitch = new Setting<>("AutoSwitch", true);
    private final Setting<Boolean> render = new Setting<>("Render", true);
    private final Setting<Color> harvestColor = new Setting<>("HarvestColor", new Color(255, 0, 0, 100), v -> render.getValue());
    private final Setting<Color> plantColor = new Setting<>("PlantColor", new Color(0, 255, 0, 100), v -> render.getValue());
    private final Setting<Color> tillColor = new Setting<>("TillColor", new Color(139, 69, 19, 100), v -> render.getValue());
    private final Setting<Color> boneMealColor = new Setting<>("BoneMealColor", new Color(200, 200, 200, 100), v -> render.getValue());
    
    private final Set<BlockPos> harvestBlocks = new HashSet<>();
    private final Set<BlockPos> plantBlocks = new HashSet<>();
    private final Set<BlockPos> tillBlocks = new HashSet<>();
    private final Map<BlockPos, Integer> boneMealBlocks = new HashMap<>();
    private int currentSlot = -1;
    private BlockPos basePos = null;
    
    public AutoFarm() {
        super("AutoFarm", Category.MISC);
    }

    @Override
    public void onEnable() {
        basePos = mc.player.getBlockPos();
        scanBlocks();
    }

    @Override
    public void onUpdate() {
        if (fullNullCheck() || basePos == null) return;
        
        try {
            // Обновляем списки блоков если они пустые
            if (harvestBlocks.isEmpty() && plantBlocks.isEmpty() && tillBlocks.isEmpty() && boneMealBlocks.isEmpty()) {
                basePos = mc.player.getBlockPos();
                scanBlocks();
            }
            
            // Обрабатываем блоки
            int actionsLeft = actionsPerTick.getValue();
            
            // Сначала собираем урожай и сразу сажаем
            if (!harvestBlocks.isEmpty() && actionsLeft > 0) {
                Iterator<BlockPos> it = harvestBlocks.iterator();
                while (it.hasNext() && actionsLeft > 0) {
                    BlockPos pos = it.next();
                    if (isValidBlock(pos)) {
                        harvestCrop(pos);
                        if (replantCrops.getValue() && hasSeedInHotbar()) {
                            plantCrop(pos);
                        }
                        actionsLeft--;
                    }
                    it.remove();
                }
            }
            
            // Сажаем на пустые места
            if (!plantBlocks.isEmpty() && actionsLeft > 0 && hasSeedInHotbar()) {
                Iterator<BlockPos> it = plantBlocks.iterator();
                while (it.hasNext() && actionsLeft > 0) {
                    BlockPos pos = it.next();
                    if (isValidPlantBlock(pos)) {
                        plantCrop(pos);
                        actionsLeft--;
                    }
                    it.remove();
                }
            }
            
            // Вспахиваем землю
            if (!tillBlocks.isEmpty() && actionsLeft > 0 && hasHoe()) {
                Iterator<BlockPos> it = tillBlocks.iterator();
                while (it.hasNext() && actionsLeft > 0) {
                    BlockPos pos = it.next();
                    if (canTill(pos)) {
                        tillGround(pos);
                        actionsLeft--;
                    }
                    it.remove();
                }
            }
            
            // Применяем костную муку
            if (!boneMealBlocks.isEmpty() && actionsLeft > 0 && useBoneMeal.getValue() && hasBoneMeal()) {
                Iterator<Map.Entry<BlockPos, Integer>> it = boneMealBlocks.entrySet().iterator();
                while (it.hasNext() && actionsLeft > 0) {
                    Map.Entry<BlockPos, Integer> entry = it.next();
                    BlockPos pos = entry.getKey();
                    int tries = entry.getValue();
                    
                    if (tries >= maxBoneMealTries.getValue()) {
                        it.remove();
                        continue;
                    }
                    
                    if (isValidBoneMealBlock(pos)) {
                        applyBoneMeal(pos);
                        entry.setValue(tries + 1);
                        actionsLeft--;
                    } else {
                        it.remove();
                    }
                }
            }
            
            // Возвращаем слот если нужно
            if (currentSlot != -1 && autoSwitch.getValue()) {
                mc.player.getInventory().selectedSlot = currentSlot;
                currentSlot = -1;
            }
        } catch (Exception e) {
            if (debug.getValue()) {
                sendMessage("Ошибка в onUpdate: " + e.getMessage());
            }
        }
    }
    
    private void scanBlocks() {
        if (fullNullCheck() || basePos == null || mc.world == null) return;

        harvestBlocks.clear();
        plantBlocks.clear();
        tillBlocks.clear();
        boneMealBlocks.clear();
        
        for (int x = -radius.getValue(); x <= radius.getValue(); x++) {
            for (int z = -radius.getValue(); z <= radius.getValue(); z++) {
                BlockPos pos = basePos.add(x, 0, z);
                if (!mc.world.isChunkLoaded(pos)) continue;
                
                Block block = mc.world.getBlockState(pos).getBlock();
                
                if (harvestCrops.getValue() && block instanceof CropBlock) {
                    CropBlock crop = (CropBlock) block;
                    if (crop.isMature(mc.world.getBlockState(pos))) {
                        harvestBlocks.add(pos);
                    } else if (useBoneMeal.getValue() && hasBoneMeal()) {
                        boneMealBlocks.put(pos, 0);
                    }
                } else if (replantCrops.getValue() && block instanceof FarmlandBlock && mc.world.getBlockState(pos.up()).getBlock() instanceof AirBlock) {
                    plantBlocks.add(pos);
                } else if (tillGround.getValue() && canTill(pos)) {
                    tillBlocks.add(pos);
                }
            }
        }
    }
    
    private boolean isValidBlock(BlockPos pos) {
        try {
            if (pos == null || mc.world == null) return false;
            if (!mc.world.isChunkLoaded(pos)) return false;
            
            Block block = mc.world.getBlockState(pos).getBlock();
            return block instanceof CropBlock && ((CropBlock) block).isMature(mc.world.getBlockState(pos));
        } catch (Exception e) {
            if (debug.getValue()) {
                sendMessage("Ошибка в isValidBlock: " + e.getMessage());
            }
            return false;
        }
    }
    
    private boolean isValidPlantBlock(BlockPos pos) {
        try {
            if (pos == null || mc.world == null) return false;
            if (!mc.world.isChunkLoaded(pos)) return false;
            
            Block block = mc.world.getBlockState(pos).getBlock();
            return block instanceof FarmlandBlock && mc.world.getBlockState(pos.up()).getBlock() instanceof AirBlock;
        } catch (Exception e) {
            if (debug.getValue()) {
                sendMessage("Ошибка в isValidPlantBlock: " + e.getMessage());
            }
            return false;
        }
    }
    
    private boolean isValidBoneMealBlock(BlockPos pos) {
        try {
            if (pos == null || mc.world == null) return false;
            if (!mc.world.isChunkLoaded(pos)) return false;
            
            Block block = mc.world.getBlockState(pos).getBlock();
            if (block instanceof CropBlock) {
                CropBlock crop = (CropBlock) block;
                return !crop.isMature(mc.world.getBlockState(pos));
            }
            return false;
        } catch (Exception e) {
            if (debug.getValue()) {
                sendMessage("Ошибка в isValidBoneMealBlock: " + e.getMessage());
            }
            return false;
        }
    }
    
    @Override
    public void onRender3D(MatrixStack stack) {
        if (!render.getValue() || fullNullCheck()) return;
        
        // Рендерим блоки для сбора урожая
        for (BlockPos pos : harvestBlocks) {
            Box box = new Box(pos);
            Render3DEngine.drawFilledBox(stack, box, harvestColor.getValue());
        }
        
        // Рендерим блоки для посадки
        for (BlockPos pos : plantBlocks) {
            Box box = new Box(pos.up());
            Render3DEngine.drawFilledBox(stack, box, plantColor.getValue());
        }
        
        // Рендерим блоки для вспахивания
        for (BlockPos pos : tillBlocks) {
            Box box = new Box(pos);
            Render3DEngine.drawFilledBox(stack, box, tillColor.getValue());
        }
        
        // Рендерим блоки для костной муки
        for (BlockPos pos : boneMealBlocks.keySet()) {
            Box box = new Box(pos);
            Render3DEngine.drawFilledBox(stack, box, boneMealColor.getValue());
        }
    }
    
    private boolean canTill(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        Block blockUp = mc.world.getBlockState(pos.up()).getBlock();
        return (block instanceof GrassBlock || block == Blocks.DIRT) 
               && blockUp instanceof AirBlock;
    }
    
    private boolean hasHoe() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof HoeItem)
                return true;
        }
        return false;
    }
    
    private boolean hasSeedInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.WHEAT_SEEDS)
                return true;
        }
        return false;
    }
    
    private boolean hasBoneMeal() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.BONE_MEAL)
                return true;
        }
        return false;
    }
    
    private void switchToTool(boolean hoe, boolean boneMeal) {
        if (!autoSwitch.getValue()) return;
        
        currentSlot = mc.player.getInventory().selectedSlot;
        
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if ((hoe && stack.getItem() instanceof HoeItem) || 
                (!hoe && !boneMeal && stack.getItem() == Items.WHEAT_SEEDS) ||
                (boneMeal && stack.getItem() == Items.BONE_MEAL)) {
                mc.player.getInventory().selectedSlot = i;
                break;
            }
        }
    }
    
    private void tillGround(BlockPos pos) {
        if (mc.interactionManager == null) return;
        
        switchToTool(true, false);
        
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }
    
    private void harvestCrop(BlockPos pos) {
        if (mc.interactionManager == null) return;
        mc.interactionManager.attackBlock(pos, Direction.UP);
    }
    
    private void plantCrop(BlockPos pos) {
        if (mc.interactionManager == null) return;
        
        switchToTool(false, false);
        
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }
    
    private void applyBoneMeal(BlockPos pos) {
        if (mc.interactionManager == null) return;
        
        switchToTool(false, true);
        
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
    }
    
    @Override
    public String getDisplayInfo() {
        return harvestBlocks.size() + "/" + plantBlocks.size() + "/" + tillBlocks.size() + "/" + boneMealBlocks.size();
    }
} 