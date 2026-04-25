package codes.morning.quickwarp.commands

import com.github.stivais.commodore.Commodore
import com.github.stivais.commodore.utils.GreedyString
import com.odtheking.odin.utils.devMessage
import com.odtheking.odin.utils.modMessage
import codes.morning.quickwarp.utils.skyblock.LocationUtils

// Commands are handled via https://github.com/Stivais/Commodore
val qwCommand = Commodore("qw", "quickwarp") {

    literal("enable").runs {

    }

    runs { greedy: GreedyString ->
        modMessage("Command with parameter executed: ${greedy.string}")
    }
}

val devCommand = Commodore("oddev") {
    literal("skyblock") {
        literal("status").runs {
            val mode = when {
                !LocationUtils.isSkyblockStateForced -> "auto"
                LocationUtils.isInSkyblock -> "on"
                else -> "off"
            }
            devMessage("SkyBlock detection mode: $mode. Effective isInSkyblock=${LocationUtils.isInSkyblock}.")
        }

        literal("on").runs {
            LocationUtils.setSkyblockStateOverride(true)
            devMessage("SkyBlock simulation forced on.")
        }

        literal("off").runs {
            LocationUtils.setSkyblockStateOverride(false)
            devMessage("SkyBlock simulation forced off.")
        }

        literal("auto").runs {
            LocationUtils.setSkyblockStateOverride(null)
            devMessage("SkyBlock simulation cleared.")
        }
    }
}