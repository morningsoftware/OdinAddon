package com.odtheking.odinaddon.features.impl.skyblock

import com.mojang.blaze3d.platform.InputConstants
import com.odtheking.odin.clickgui.settings.Setting.Companion.withDependency
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.KeybindSetting
import com.odtheking.odin.clickgui.settings.impl.KeybindSetting.Companion.isDown
import com.odtheking.odin.clickgui.settings.impl.NumberSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.RenderEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.core.EventPriority
import com.odtheking.odin.events.core.on
import com.odtheking.odin.events.core.onSend
import com.odtheking.odin.features.Module
import com.odtheking.odin.features.impl.render.Etherwarp
import com.odtheking.odin.utils.Color.Companion.withAlpha
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.getBlockBounds
import com.odtheking.odin.utils.isEtherwarpItem
import com.odtheking.odin.utils.render.drawStyledBox
import com.odtheking.odinaddon.utils.randInt
import com.odtheking.odinaddon.utils.skyblock.LocationUtils
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.phys.AABB

object QuickWarp : Module(
    name = "QuickWarp",
    description = "Introduces new, faster ways to Etherwarp when using an Etherwarp-enabled item."
) {
    private enum class EtherwarpState { IDLE, SNEAKING, INTERACTING }
    private var state = EtherwarpState.IDLE

    private var cooldown by NumberSetting("Cooldown", 300L, 100L, max = 500L, desc = "Cooldown of using QuickWarp (in milliseconds).")
    var delaySpeed by SelectorSetting(name = "Delay Speed", default = "Medium", options = arrayListOf("Fast", "Medium", "Slow"), desc = "Delay speed of QuickWarp")

    val quickWarpKey by KeybindSetting(
        name = "QuickWarp Key",
        default = InputConstants.UNKNOWN,
        desc = "Key to trigger QuickWarp."
    )

    val instantWarp by BooleanSetting(name = "InstantWarp", default = false, desc = "Alternative mode of QuickWarp, which always shows overlay when item is held, and instantly warps upon left-clicking.")

    val instantWarpKey by KeybindSetting(
        name = "InstantWarp Key",
        default = InputConstants.UNKNOWN,
        desc = "Key to trigger InstantWarp."
    ).withDependency { instantWarp }

    val alwaysOnOverlay by BooleanSetting(name = "Always-On Overlay", default = false, desc = "Display Etherwarp Overlay when holding Etherwarp item.")

    val alwaysOnOverlayColor by ColorSetting(
        name = "Overlay Color",
        default = Colors.MINECRAFT_GOLD.withAlpha(.85f),
        allowAlpha = true,
        desc = "Color of the always-on overlay."
    ).withDependency { alwaysOnOverlay }

    private data class DelayProfile(
        val sneakMin: Int, val sneakMax: Int,
        val releaseMin: Int, val releaseMax: Int,
        val jumpMin: Int, val jumpMax: Int
    )

    private val delayProfiles = mapOf(
        0 to DelayProfile(sneakMin = 1, sneakMax = 2, releaseMin = 1, releaseMax = 2, jumpMin = 1, jumpMax = 3),
        1 to DelayProfile(sneakMin = 3, sneakMax = 6, releaseMin = 3, releaseMax = 5, jumpMin = 1, jumpMax = 4),
        2 to DelayProfile(sneakMin = 4, sneakMax = 8, releaseMin = 3, releaseMax = 7, jumpMin = 2, jumpMax = 5)
    )

    private fun currentProfile() = delayProfiles[delaySpeed] ?: delayProfiles[1]!!

    fun shouldSuppressLeftClick(): Boolean =
        enabled && LocationUtils.isInSkyblock && mc.player?.mainHandItem?.isEtherwarpItem() != null

    private var reqSneakTicks = 0
    private var curSneakTicks = 0
    private var reqReleaseTicks = 0
    private var curReleaseTicks = 0
    private var reqJumpTicks = 0
    private var curJumpTicks = 0

    private var jumpLocked = false
    private var vertLocked = false

    private var lastTeleportTime = 0L

    private var instantWarpTrigger = false
    private var quickWarpTrigger = false

    private var instantWarpAlrTrigger = false
    private var quickWarpAlrTrigger = false

    init {
        on<TickEvent.Start> {
            if (!enabled) return@on

            if (state != EtherwarpState.IDLE || vertLocked) {
                mc.options.keyUse.isDown = false
            }
        }
        onSend<ServerboundUseItemPacket> {
            if (!enabled) return@onSend
            if (!LocationUtils.isInSkyblock) return@onSend
            if (mc.player?.mainHandItem?.isEtherwarpItem() == null) return@onSend

            val locked = state != EtherwarpState.IDLE || vertLocked

            if (locked) {
                it.cancel()
            }
        }

        on<RenderEvent.Extract>(EventPriority.LOW) {
            if (!enabled || !instantWarp || !alwaysOnOverlay) return@on
            val mc = mc ?: return@on
            if (mc.screen != null) return@on

            val mainHandItem = mc.player?.mainHandItem ?: return@on
            val etherData = mainHandItem.isEtherwarpItem() ?: return@on

            val etherPos = Etherwarp.getEtherPos(
                mc.player?.position(),
                57.0 + (etherData.getInt("tuned_transmission").orElse(0)),
                etherWarp = true
            )

            if (etherPos.succeeded != true) return@on
            etherPos.pos?.let { pos ->
                val box = pos.getBlockBounds()?.move(pos) ?: AABB(pos)
                drawStyledBox(box, alwaysOnOverlayColor, 1, false)
            }
        }

        // Makes the player not move vertically while holding left-click for the TP.
        on<TickEvent.Start> {
            val player = mc.player ?: return@on
            if (vertLocked) {
                mc.options.keyShift.isDown = true
                if (player.abilities.flying) {
                    curJumpTicks++
                    if (curJumpTicks >= reqJumpTicks) {
                        mc.options.keyJump.isDown = true
                        jumpLocked = true
                    }
                }
            }
        }

        onSend<ServerboundSwingPacket> {
            if (!enabled) return@onSend
            val held = mc.player?.mainHandItem ?: return@onSend
            if (held.isEtherwarpItem() == null) return@onSend
            it.cancel()
        }

        onSend<ServerboundPlayerActionPacket> {
            if (!shouldSuppressLeftClick()) return@onSend
            when (action) {
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK -> it.cancel()
                else -> {}
            }
        }

        on<TickEvent.End> {
            if (!LocationUtils.isInSkyblock) return@on
            val player = mc.player ?: return@on

            when (state) {
                EtherwarpState.SNEAKING -> {
                    curSneakTicks++
                    if (curSneakTicks >= reqSneakTicks && (instantWarpTrigger || !quickWarpKey.isDown())) {
                        mc.gameMode?.useItem(player, player.usedItemHand)
                        state = EtherwarpState.INTERACTING
                    }
                }

                EtherwarpState.INTERACTING -> {
                    curReleaseTicks++
                    if (curReleaseTicks >= reqReleaseTicks) {
                        mc.options.keyShift.isDown = InputConstants.getKey(mc.options.keyShift.saveString()).isDown()
                        mc.options.keyJump.isDown = InputConstants.getKey(mc.options.keyJump.saveString()).isDown()
                        vertLocked = false
                        jumpLocked = false
                        state = EtherwarpState.IDLE
                    }
                }

                EtherwarpState.IDLE -> {}
            }

            if (state == EtherwarpState.IDLE) {
                val held = player.mainHandItem
                if (held.isEtherwarpItem() == null) return@on

                quickWarpTrigger = quickWarpKey.isDown()
                instantWarpTrigger = instantWarpKey.isDown()

                val curTime = System.currentTimeMillis()

                val quickPressed = quickWarpTrigger && !quickWarpAlrTrigger
                val instantPressed = instantWarp && instantWarpTrigger && !instantWarpAlrTrigger

                val triggered = quickPressed || instantPressed

                // Do not allow holding left-click for multiple TPs, and only allow one TP every 500ms.
                // Without either of these checks, there is a high chance for AC flags.
                if (triggered && curTime - lastTeleportTime >= cooldown) {
                    vertLocked = true
                    lastTeleportTime = curTime
                    var profile = currentProfile()

                    // Get random values for delays based on the selected profile.
                    reqSneakTicks = randInt(profile.sneakMin, profile.sneakMax)
                    reqReleaseTicks = randInt(profile.releaseMin, profile.releaseMax)
                    reqJumpTicks = randInt(profile.jumpMin, profile.jumpMax)

                    // Reset counters to zero.
                    curSneakTicks = 0
                    curReleaseTicks = 0
                    curJumpTicks = 0

                    state = EtherwarpState.SNEAKING
                }
                quickWarpAlrTrigger = quickWarpTrigger
                instantWarpAlrTrigger = instantWarpTrigger
            }
        }
    }
}
