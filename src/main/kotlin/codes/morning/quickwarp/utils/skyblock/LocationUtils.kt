package codes.morning.quickwarp.utils.skyblock

object LocationUtils {

    private var forcedSkyblockState: Boolean? = null

    var isInSkyblock: Boolean = false
        get() = forcedSkyblockState ?: field
        private set

    val isSkyblockStateForced: Boolean
        get() = forcedSkyblockState != null

    fun setSkyblockStateOverride(state: Boolean?) {
        forcedSkyblockState = state
    }
}
