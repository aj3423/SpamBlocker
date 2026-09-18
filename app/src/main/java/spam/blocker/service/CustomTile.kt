package spam.blocker.service

import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import androidx.compose.runtime.MutableState
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.BotTable
import spam.blocker.service.bot.QuickTile
import spam.blocker.util.spf

open class CustomTileBase(
    val state: MutableState<Boolean>,
    val tileIndex: Int, // 1, 2, ...
) : TileService() {

    private fun update() {
        val spf = spf.BotOptions(this)
        val enabled = spf.isDynamicTileEnabled(tileIndex)
        qsTile.state = if (enabled) STATE_ACTIVE else STATE_INACTIVE

        // Sync the title with the bound workflow.desc
        val boundBot = BotTable.listAll(this).find {
            (it.trigger is QuickTile) && it.trigger.tileIndex == tileIndex
        }
        qsTile.label = boundBot?.desc ?: getString(R.string.custom)

        qsTile.updateTile()
    }
    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        val spf = spf.BotOptions(this)

        val enabled = spf.isDynamicTileEnabled(tileIndex)
        spf.setDynamicTileEnabled(tileIndex, !enabled) // toggle

        update()

        state.value = !enabled
    }
}

class CustomTile : CustomTileBase(G.dynamicTile0Enabled, 0)