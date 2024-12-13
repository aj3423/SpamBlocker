package spam.blocker.service

import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import spam.blocker.G
import spam.blocker.util.spf

class Tile : TileService() {

    private fun update() {
        val spf = spf.Global(this)
        val enabled = spf.isGloballyEnabled()
        qsTile.state = if (enabled) STATE_ACTIVE else STATE_INACTIVE

        qsTile.updateTile()
    }
    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        val spf = spf.Global(this)
        spf.toggleGloballyEnabled()

        update()

        G.globallyEnabled.value = spf.isGloballyEnabled()
    }
}