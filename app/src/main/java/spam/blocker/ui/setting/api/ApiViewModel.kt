package spam.blocker.ui.setting.api

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import spam.blocker.db.ApiTable
import spam.blocker.db.IApi
import spam.blocker.db.QueryApiTable
import spam.blocker.db.ReportApiTable
import spam.blocker.def.Def
import spam.blocker.util.spf

open class ApiViewModel(
    val table: ApiTable,
    val forType: Int,
) {
    val apis = mutableStateListOf<IApi>()
    val listCollapsed = mutableStateOf(false)


    fun reloadDb(ctx: Context) {
        apis.clear()

        val all = table.listAll(ctx)
        apis.addAll(all)

        listCollapsed.value = if (forType == Def.ForApiQuery)
            spf.ApiQueryOptions(ctx).isListCollapsed
        else
            spf.ApiReportOptions(ctx).isListCollapsed
    }

    fun toggleCollapse(ctx: Context) {
        // don't collapse if it's empty
        if (apis.isEmpty() && !listCollapsed.value) {
            return
        }

        listCollapsed.value = !listCollapsed.value
        when(forType) {
            Def.ForApiQuery -> spf.ApiQueryOptions(ctx).isListCollapsed = listCollapsed.value
            Def.ForApiReport -> spf.ApiReportOptions(ctx).isListCollapsed = listCollapsed.value
        }
    }
}

class ApiQueryViewModel : ApiViewModel(QueryApiTable(), Def.ForApiQuery)
class ApiReportViewModel : ApiViewModel(ReportApiTable(), Def.ForApiReport)
