package spam.blocker.ui.setting

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat

import androidx.recyclerview.widget.RecyclerView;
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.R
import spam.blocker.util.AppInfo

class PopupAppListAdapter(
    private var selected: ObservableArrayList<String>,
    private var filtered: ObservableArrayList<AppInfo>
) : RecyclerView.Adapter<PopupAppListAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.popup_app_item, parent, false);

        return Holder(view);
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = filtered[position]

        holder.imgAppIcon.setImageDrawable(app.icon)
        holder.labelAppLabel.text = app.label
        holder.labelAppPkg.text = app.pkgName
        holder.switchAppEnabled.isChecked = selected.contains(app.pkgName)
        holder.switchAppEnabled.setOnClickListener {
            if (holder.switchAppEnabled.isChecked) { // add to selected
                selected.add(app.pkgName)
            } else { // remove from selected
                val i = selected.indexOf(app.pkgName)
                selected.removeAt(i)
            }
        }
    }

    override fun getItemCount() = filtered.size

    class Holder : RecyclerView.ViewHolder {
        var imgAppIcon: ImageView
        var labelAppLabel: TextView
        var labelAppPkg: TextView
        var switchAppEnabled: SwitchCompat

        constructor(itemView: View) : super(itemView) {
            imgAppIcon = itemView.findViewById(R.id.popup_img_app_icon)
            labelAppLabel = itemView.findViewById(R.id.text_app_label)
            labelAppPkg = itemView.findViewById(R.id.text_app_pkg)
            switchAppEnabled = itemView.findViewById(R.id.switch_app_enabled)
        }
    }
}
