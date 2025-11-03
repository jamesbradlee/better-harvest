package io.jmsn.mc.betterharvest

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.HoeItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

@Mod(JamesonsBetterHarvest.ID)
@EventBusSubscriber(modid = JamesonsBetterHarvest.ID, value = [Dist.CLIENT, Dist.DEDICATED_SERVER])
object JamesonsBetterHarvest {
    const val ID = "jamesons_better_harvest"
    private val log: Logger = LogManager.getLogger(ID)

    init {
        runForDist(
            clientTarget = { MOD_BUS.addListener(::onClientSetup) },
            serverTarget = { MOD_BUS.addListener(::onServerSetup) },
        )
    }

    private fun onClientSetup(
        @Suppress("unused") event: FMLClientSetupEvent,
    ) {
        log.debug("Why am I here?")
    }

    private fun onServerSetup(
        @Suppress("unused") event: FMLDedicatedServerSetupEvent,
    ) {
        log.debug("\uD83D\uDC4B")
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.level.isClientSide) return

        // Ensure the player is holding a hoe, otherwise don't do anything
        if (event.entity.mainHandItem.item !is HoeItem) return

        val blockState = event.level.getBlockState(event.pos)

        when {
            blockState.block is CropBlock -> harvestCrop(event.level, event.pos, blockState.block as CropBlock, blockState, event.entity)
            blockState.`is`(Blocks.CACTUS) -> harvestStacked(event.level, event.pos, Blocks.CACTUS, event.entity)
            blockState.`is`(Blocks.SUGAR_CANE) -> harvestStacked(event.level, event.pos, Blocks.SUGAR_CANE, event.entity)
            else -> return
        }
    }

    private fun harvestCrop(
        level: Level,
        pos: BlockPos,
        crop: CropBlock,
        blockState: BlockState,
        player: Player,
    ) {
        if (!crop.isMaxAge(blockState)) return

        Block.dropResources(blockState, level, pos)
        level.setBlock(pos, crop.getStateForAge(0), 2)
        level.playSound(null, pos, blockState.getSoundType(level, pos, player).breakSound, SoundSource.BLOCKS, 1f, 1f)
    }

    private fun harvestStacked(
        level: Level,
        pos: BlockPos,
        block: Block,
        player: Player,
    ) {
        val server = level as? ServerLevel ?: return

        var bottom = pos
        while (level.getBlockState(bottom.below()).`is`(block)) {
            bottom = bottom.below()
        }
        val blockState = level.getBlockState(bottom)

        val loot = mutableListOf<ItemStack>()
        val positions = mutableListOf<BlockPos>()
        var cursor = bottom.above()
        var blockStateAtCursor = level.getBlockState(cursor)

        while (true) {
            loot.addAll(Block.getDrops(blockStateAtCursor, server, cursor, null, player, player.mainHandItem))

            positions.add(cursor)

            cursor = cursor.above()
            blockStateAtCursor = level.getBlockState(cursor)

            if (!blockStateAtCursor.`is`(block)) {
                break
            }
        }

        // Remove blocks from bottom to top
        for (p in positions.reversed()) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3)
        }

        // Drop merged stacks at the bottom position
        for (item in mergeItemStacks(loot)) {
            Block.popResource(level, bottom, item)
        }

        level.playSound(null, pos, blockState.getSoundType(level, pos, player).breakSound, SoundSource.BLOCKS, 1f, 1f)
    }

    private fun mergeItemStacks(stacks: List<ItemStack>): List<ItemStack> {
        val merged = mutableListOf<ItemStack>()
        for (stack in stacks) {
            val maxStackSize = stack.maxStackSize

            // TODO(jamesbradlee): this should be optimized with some form of map or hash code to avoid O(n^2) behavior
            val existing = merged.find { it.count < maxStackSize && ItemStack.isSameItemSameComponents(it, stack) }

            if (existing == null) {
                merged.add(stack.copy())
                continue
            }

            val space = maxStackSize - existing.count
            val toMove = stack.count.coerceAtMost(space)
            existing.grow(toMove)

            var remaining = stack.count - toMove

            while (remaining > 0) {
                val toAdd = remaining.coerceAtMost(maxStackSize)
                merged.add(stack.copyWithCount(toAdd))
                remaining -= toAdd
            }
        }

        return merged
    }
}
