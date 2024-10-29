package spam.blocker.ui.main

import android.content.Context
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(DelicateCoroutinesApi::class)
fun debug(ctx: Context) {
    GlobalScope.launch {
        withContext(Dispatchers.Main) {
            while (true) {
                delay(1000)

                val zoom = "us.zoom.videomeetings"
                val sb = "spam.blocker"
                val skype = "com.skype.raider"

//                val eventsMap = getAppsEvents(ctx, setOf(skype, sb, zoom))
//
//                val skypeRunning = isForegroundServiceRunning(eventsMap[skype])
//                val sbRunning = isForegroundServiceRunning(eventsMap[sb])
//                val zoomRunning = isForegroundServiceRunning(eventsMap[zoom])
//                loge("sb: $sbRunning, skype: $skypeRunning, zoom: $zoomRunning")
            }
        }
    }
}