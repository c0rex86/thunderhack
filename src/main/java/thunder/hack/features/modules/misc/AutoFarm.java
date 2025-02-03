package thunder.hack.features.modules.misc;

import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.setting.impl.BooleanSettingGroup;
import thunder.hack.setting.impl.ColorSetting;
import thunder.hack.setting.impl.SettingGroup;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.InventoryUtility;
import thunder.hack.utility.player.SearchInvResult;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AutoFarm extends Module {
    private final Setting<SettingGroup> sgGeneral = new Setting<>("General", new SettingGroup(false, 0));
    private final Setting<Integer> range = new Setting<>("Range", 4, 1, 6).addToGroup(sgGeneral);
    private final Setting<Integer> delay = new Setting<>("Delay", 150, 0, 1000).addToGroup(sgGeneral);
    private final Setting<Boolean> autoReplant = new Setting<>("AutoReplant", true).addToGroup(sgGeneral);
    private final Setting<Boolean> autoCollect = new Setting<>("AutoCollect", true).addToGroup(sgGeneral);
    private final Setting<Boolean> rotations = new Setting<>("Rotations", true).addToGroup(sgGeneral);
    private final Setting<Integer> blocksPerTick = new Setting<>("BlocksPerTick", 2, 1, 5).addToGroup(sgGeneral);
    private final Setting<Boolean> noSwing = new Setting<>("NoSwing", false).addToGroup(sgGeneral);
    private final Setting<Boolean> autoSwitch = new Setting<>("AutoSwitch", true).addToGroup(sgGeneral);
    private final Setting<Boolean> returnSlot = new Setting<>("ReturnSlot", true).addToGroup(sgGeneral);

    private final Setting<SettingGroup> sgCrops = new Setting<>("Crops", new SettingGroup(false, 0));
    private final Setting<Boolean> wheat = new Setting<>("Wheat", true).addToGroup(sgCrops);
    private final Setting<Boolean> carrots = new Setting<>("Carrots", true).addToGroup(sgCrops);
    private final Setting<Boolean> potatoes = new Setting<>("Potatoes", true).addToGroup(sgCrops);
    private final Setting<Boolean> beetroots = new Setting<>("Beetroots", true).addToGroup(sgCrops);
    private final Setting<Boolean> netherwart = new Setting<>("NetherWart", true).addToGroup(sgCrops);
    private final Setting<Boolean> cocoa = new Setting<>("Cocoa", true).addToGroup(sgCrops);
    private final Setting<Boolean> melons = new Setting<>("Melons", true).addToGroup(sgCrops);
    private final Setting<Boolean> pumpkins = new Setting<>("Pumpkins", true).addToGroup(sgCrops);
    private final Setting<Boolean> sugarcane = new Setting<>("Sugarcane", true).addToGroup(sgCrops);
    private final Setting<Boolean> cactus = new Setting<>("Cactus", true).addToGroup(sgCrops);
    private final Setting<Boolean> bamboo = new Setting<>("Bamboo", true).addToGroup(sgCrops);

    private final Setting<BooleanSettingGroup> render = new Setting<>("Render", new BooleanSettingGroup(true));
    private final Setting<Boolean> renderTarget = new Setting<>("RenderTarget", true).addToGroup(render);
    private final Setting<ColorSetting> harvestColor = new Setting<>("HarvestColor", new ColorSetting(new Color(255, 50, 50, 100))).addToGroup(render);
    private final Setting<ColorSetting> plantColor = new Setting<>("PlantColor", new ColorSetting(new Color(50, 255, 50, 100))).addToGroup(render);

    private final Timer actionTimer = new Timer();
    private final List<BlockPos> targetBlocks = new ArrayList<>();
    private BlockPos currentTarget;
    private int originalSlot = -1;

    public AutoFarm() {
        super("AutoFarm", Category.MISC);
    }

    @Override
    public void onEnable() {
        targetBlocks.clear();
        currentTarget = null;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        if (returnSlot.getValue() && originalSlot != -1) {
            mc.player.getInventory().selectedSlot = originalSlot;
            originalSlot = -1;
        }
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;

        if (targetBlocks.isEmpty()) {
            findTargetBlocks();
        }

        if (!actionTimer.passedMs(delay.getValue())) return;

        int processed = 0;
        List<BlockPos> toRemove = new ArrayList<>();

        for (BlockPos pos : targetBlocks) {
            if (processed >= blocksPerTick.getValue()) break;

            BlockState state = mc.world.getBlockState(pos);
            if (shouldHarvest(state, pos)) {
                if (harvest(pos)) {
                    processed++;
                    toRemove.add(pos);
                }
            } else if (shouldPlant(state, pos)) {
                if (plant(pos)) {
                    processed++;
                    toRemove.add(pos);
                }
            } else {
                toRemove.add(pos);
            }
        }

        targetBlocks.removeAll(toRemove);
        actionTimer.reset();
    }

    private void findTargetBlocks() {
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    
                    if (isTargetBlock(state.getBlock()) || (autoReplant.getValue() && canPlantOn(state.getBlock()))) {
                        targetBlocks.add(pos);
                    }
                }
            }
        }
    }

    private boolean shouldHarvest(BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        
        if (block instanceof CropBlock) {
            if (!isCropEnabled(block)) return false;
            return ((CropBlock) block).isMature(state);
        }
        
        if (block instanceof NetherWartBlock && netherwart.getValue()) {
            return state.get(NetherWartBlock.AGE) >= 3;
        }
        
        if (block instanceof CocoaBlock && cocoa.getValue()) {
            return state.get(CocoaBlock.AGE) >= 2;
        }
        
        if ((block == Blocks.MELON || block == Blocks.PUMPKIN) && (melons.getValue() || pumpkins.getValue())) return true;
        if (block == Blocks.SUGAR_CANE && sugarcane.getValue()) return true;
        if (block == Blocks.CACTUS && cactus.getValue()) return true;
        if (block == Blocks.BAMBOO && bamboo.getValue()) return true;

        return false;
    }

    private boolean shouldPlant(BlockState state, BlockPos pos) {
        if (!autoReplant.getValue()) return false;
        
        Block block = state.getBlock();
        return block == Blocks.AIR && canPlantOn(mc.world.getBlockState(pos.down()).getBlock());
    }

    private boolean harvest(BlockPos pos) {
        if (rotations.getValue()) {
            // Implement rotation logic here
        }

        BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos, false);
        mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
        
        if (!noSwing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        return true;
    }

    private boolean plant(BlockPos pos) {
        Item seedItem = getSeedForPos(pos);
        if (seedItem == null) return false;

        if (autoSwitch.getValue()) {
            SearchInvResult seedSlot = InventoryUtility.findItemInHotBar(seedItem);
            if (!seedSlot.found()) return false;

            if (originalSlot == -1) {
                originalSlot = mc.player.getInventory().selectedSlot;
            }
            mc.player.getInventory().selectedSlot = seedSlot.slot();
        }

        BlockHitResult hit = new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, pos.down(), false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

        if (!noSwing.getValue()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        return true;
    }

    private boolean isCropEnabled(Block block) {
        if (block == Blocks.WHEAT && wheat.getValue()) return true;
        if (block == Blocks.CARROTS && carrots.getValue()) return true;
        if (block == Blocks.POTATOES && potatoes.getValue()) return true;
        if (block == Blocks.BEETROOTS && beetroots.getValue()) return true;
        return false;
    }

    private boolean isTargetBlock(Block block) {
        return (block instanceof CropBlock && isCropEnabled(block)) ||
               (block instanceof NetherWartBlock && netherwart.getValue()) ||
               (block instanceof CocoaBlock && cocoa.getValue()) ||
               ((block == Blocks.MELON || block == Blocks.PUMPKIN) && (melons.getValue() || pumpkins.getValue())) ||
               (block == Blocks.SUGAR_CANE && sugarcane.getValue()) ||
               (block == Blocks.CACTUS && cactus.getValue()) ||
               (block == Blocks.BAMBOO && bamboo.getValue());
    }

    private boolean canPlantOn(Block block) {
        return block == Blocks.FARMLAND || 
               block == Blocks.SOUL_SAND || 
               block == Blocks.JUNGLE_LOG || 
               block == Blocks.DIRT || 
               block == Blocks.GRASS_BLOCK;
    }

    private Item getSeedForPos(BlockPos pos) {
        Block downBlock = mc.world.getBlockState(pos.down()).getBlock();
        
        if (downBlock == Blocks.FARMLAND) {
            if (wheat.getValue()) return Items.WHEAT_SEEDS;
            if (carrots.getValue()) return Items.CARROT;
            if (potatoes.getValue()) return Items.POTATO;
            if (beetroots.getValue()) return Items.BEETROOT_SEEDS;
        } else if (downBlock == Blocks.SOUL_SAND && netherwart.getValue()) {
            return Items.NETHER_WART;
        } else if (downBlock == Blocks.JUNGLE_LOG && cocoa.getValue()) {
            return Items.COCOA_BEANS;
        }
        
        return null;
    }

    @Override
    public String getDisplayInfo() {
        return targetBlocks.size() + " blocks";
    }
} 