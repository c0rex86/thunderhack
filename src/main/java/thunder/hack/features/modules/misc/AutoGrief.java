package thunder.hack.features.modules.misc;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.player.PlayerUtility;

import java.util.ArrayList;
import java.util.List;

public class AutoGrief extends Module {
    private final Setting<Integer> range = new Setting<>("Range", 4, 1, 6);
    private final Setting<Integer> blocksPerTick = new Setting<>("BlocksPerTick", 2, 1, 5);
    private final Setting<Boolean> onlyValuable = new Setting<>("OnlyValuable", true);
    private final List<BlockPos> targetBlocks = new ArrayList<>();
    
    public AutoGrief() {
        super("AutoGrief", Category.MISC);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;

        if (targetBlocks.isEmpty()) {
            findTargetBlocks();
        }

        int broken = 0;
        List<BlockPos> toRemove = new ArrayList<>();

        for (BlockPos pos : targetBlocks) {
            if (broken >= blocksPerTick.getValue()) break;

            Block block = mc.world.getBlockState(pos).getBlock();
            if (block == Blocks.AIR) {
                toRemove.add(pos);
                continue;
            }

            Vec3d eyesPos = mc.player.getEyePos();
            Vec3d blockCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            
            if (eyesPos.distanceTo(blockCenter) > range.getValue()) {
                continue;
            }

            BlockHitResult hit = new BlockHitResult(blockCenter, Direction.UP, pos, false);
            mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
            mc.player.swingHand(Hand.MAIN_HAND);
            
            broken++;
            toRemove.add(pos);
        }

        targetBlocks.removeAll(toRemove);
    }

    private void findTargetBlocks() {
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (block == Blocks.AIR) continue;

                    if (onlyValuable.getValue()) {
                        if (isValuableBlock(block)) {
                            targetBlocks.add(pos);
                        }
                    } else {
                        targetBlocks.add(pos);
                    }
                }
            }
        }
    }

    private boolean isValuableBlock(Block block) {
        return block == Blocks.DIAMOND_BLOCK ||
               block == Blocks.EMERALD_BLOCK ||
               block == Blocks.GOLD_BLOCK ||
               block == Blocks.IRON_BLOCK ||
               block == Blocks.CHEST ||
               block == Blocks.TRAPPED_CHEST ||
               block == Blocks.ENDER_CHEST ||
               block == Blocks.SHULKER_BOX;
    }
} 