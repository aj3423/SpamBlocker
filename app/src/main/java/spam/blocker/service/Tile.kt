package spam.blocker.service

import android.content.Intent
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.TileService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.Global

class Tile : TileService() {

    private fun update() {
        val spf = Global(this)
        val enabled = spf.isGloballyEnabled()
        qsTile.state = if (enabled) STATE_ACTIVE else STATE_INACTIVE

        qsTile.updateTile()
    }
    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        Global(this).toggleGloballyEnabled()

        update()

        val intent = Intent(Def.ACTION_TILE_TOGGLE)
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.sendBroadcast(intent)
    }
}