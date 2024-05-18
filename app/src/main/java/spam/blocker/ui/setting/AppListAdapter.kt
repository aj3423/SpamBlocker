package spam.blocker.ui.setting

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView

import androidx.recyclerview.widget.RecyclerView;
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.AppInfo
import spam.blocker.util.Util.Companion.getAppsMap
import spam.blocker.util.Util.Companion.listApps

class AppListAdapter(
    private val ctx: Context,
    private val selected: ObservableArrayList<String>,
    private val onClick: ()->Unit

) :
    RecyclerView.Adapter<AppListAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_icon_item, parent, false);

        return Holder(view);
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val pkgName = selected[position]

        holder.itemView.setOnClickListener { onClick() }
        holder.imgIcon.setImageDrawable(getAppsMap(ctx)[pkgName]?.icon)
    }

    override fun getItemCount() = selected.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var imgIcon: ImageView

        init {
            imgIcon = itemView.findViewById(R.id.recycler_app_icon)
        }
    }
}
