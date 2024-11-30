package spam.blocker.ui.setting.api

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.Api
import spam.blocker.db.ApiTable
import spam.blocker.util.SharedPref.ApiOptions

class ApiViewModel {
    val apis = mutableStateListOf<Api>()
    val listCollapsed = mutableStateOf(false)


    fun reload(ctx: Context) {
        apis.clear()

        val all = ApiTable.listAll(ctx)
        apis.addAll(all)

        listCollapsed.value = ApiOptions(ctx).isListCollapsed()
    }

    fun toggleCollapse(ctx: Context) {
        // don't collapse if it's empty
        if (apis.isEmpty() && !listCollapsed.value) {
            return
        }

        listCollapsed.value = !listCollapsed.value
        ApiOptions(ctx).setListCollapsed(listCollapsed.value)
    }
}
