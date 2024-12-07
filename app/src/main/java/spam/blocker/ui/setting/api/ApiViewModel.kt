package spam.blocker.ui.setting.api

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.Api
import spam.blocker.db.ApiTable
import spam.blocker.db.Db
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.ApiQueryOptions
import spam.blocker.util.SharedPref.ApiReportOptions

open class ApiViewModel(
    val table: ApiTable,
    val forType: Int,
) {
    val apis = mutableStateListOf<Api>()
    val listCollapsed = mutableStateOf(false)


    fun reloadDb(ctx: Context) {
        apis.clear()

        val all = table.listAll(ctx)
        apis.addAll(all)

        listCollapsed.value = if (forType == Def.ForApiQuery)
            ApiQueryOptions(ctx).isListCollapsed()
        else
            ApiReportOptions(ctx).isListCollapsed()
    }

    fun toggleCollapse(ctx: Context) {
        // don't collapse if it's empty
        if (apis.isEmpty() && !listCollapsed.value) {
            return
        }

        listCollapsed.value = !listCollapsed.value
        ApiQueryOptions(ctx).setListCollapsed(listCollapsed.value)
    }
}

class ApiQueryViewModel : ApiViewModel(ApiTable(Db.TABLE_API_QUERY), Def.ForApiQuery)
class ApiReportViewModel : ApiViewModel(ApiTable(Db.TABLE_API_REPORT), Def.ForApiReport)
