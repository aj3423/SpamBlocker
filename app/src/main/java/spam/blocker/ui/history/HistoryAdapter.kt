package spam.blocker.ui.history


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.R
import spam.blocker.db.Db
import spam.blocker.db.Record
import spam.blocker.db.HistoryTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.PatternRule
import spam.blocker.def.Def
import spam.blocker.service.Checker
import spam.blocker.ui.setting.PopupEditRuleFragment
import spam.blocker.ui.util.Button
import spam.blocker.ui.util.UI.Companion.setRoundImage
import spam.blocker.ui.util.UI.Companion.showIf
import spam.blocker.ui.util.dynamicPopupMenu
import spam.blocker.util.AppInfo
import spam.blocker.util.Clipboard
import spam.blocker.util.Contacts
import spam.blocker.util.Launcher
import spam.blocker.util.Util


class HistoryAdapter(
    private val fragment: Fragment,
    private var table: HistoryTable,
    private var records: ObservableArrayList<Record>
) :
    RecyclerView.Adapter<HistoryAdapter.Holder>()
{

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.record, parent, false);

        return Holder(view);
    }



    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ctx = fragment.requireContext()
        val green = ctx.resources.getColor(R.color.text_green, null)
        val red = ctx.resources.getColor(R.color.salmon, null)

        val record = records[position]

        val contact = Contacts.findByRawNumber(ctx, record.peer)

        val bmpAvatar = contact?.loadAvatar(ctx)
        if (bmpAvatar != null) {
            setRoundImage(holder.imgPhoto, bmpAvatar)
        } else {
            val drawable = holder.imgPhoto.background.mutate()
            // use the hash code as color
            val toHash = contact?.name ?: record.peer
            val color = toHash.hashCode() or Color.parseColor("#808080") // higher contrast
            DrawableCompat.setTint(drawable, color)
        }
        holder.labelPeer.text = contact?.name ?: record.peer
        holder.labelPeer.setTextColor(if (record.isBlocked()) red else green)
        holder.labelResult.text = Checker.resultStr(ctx, record.result, record.reason)
        showIf(holder.unreadMark, !record.read, true)
        if (record.result == Def.RESULT_ALLOWED_BY_RECENT_APP) {
            holder.imgReason.setImageDrawable(AppInfo.fromPackage(ctx, record.reason).icon)
        }
        showIf(holder.imgReason, record.result == Def.RESULT_ALLOWED_BY_RECENT_APP)

        // mark the clicked record as read
        holder.itemView.setOnClickListener {
            // get the clicked index again, there may be new incoming call/sms inserted
            //  to the list after the binding
            val i = records.indexOfFirst{ it.id == record.id}
            val clickedRecord = records[i]
            if(!clickedRecord.read) {
                table.markAsRead(ctx, clickedRecord.id)
                clickedRecord.read = true
                records[i] = clickedRecord
            }

            // Navigate to the default Call/SMS app, and open the conversation to this number
            when(table.tableName()) {
                Db.TABLE_CALL -> {
                    Launcher.openCallConversation(ctx, clickedRecord.peer)
                }
                Db.TABLE_SMS -> {
                    Launcher.openSMSConversation(ctx, clickedRecord.peer)
                }
            }
        }

        // context menu
        holder.itemView.setOnLongClickListener {
            val items = ctx.resources.getStringArray(R.array.history_record_context_menu).asList()
            dynamicPopupMenu(ctx, holder.itemView, items.map {
                Button(it)
            }, { clickedIndex ->
                when (clickedIndex) {
                    0 -> { // copy as raw number
                        Clipboard.copy(ctx, record.peer)
                    }
                    1 -> { // add to new rule
                        val defaultRule = PatternRule().apply {
                            pattern = Util.clearNumber(record.peer)
                        }

                        PopupEditRuleFragment().apply {
                            initFilter = defaultRule
                            handleSave = { newRule -> // callback
                                // 1. add to db
                                NumberRuleTable().addNewRule(ctx, newRule)
                            }
                            forType = Def.ForNumber
                        }.show(fragment.requireActivity().supportFragmentManager, "tag_edit_filter") // use a different tag here
                    }
                }
            })

            true
        }

        // time
        if (Util.isToday(record.time)) {
            holder.labelTime.text = Util.hourMin(record.time)
        } else if (Util.isWithinAWeek(record.time)) {
            holder.labelTime.text = Util.getDayOfWeek(ctx, record.time) + "\n" + Util.hourMin(record.time)
        } else {
            holder.labelTime.text = Util.fullDateString(record.time)
        }
    }

    override fun getItemCount() = records.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var imgPhoto: ImageView
        var labelPeer: TextView
        var labelResult: TextView
        var imgReason: ImageView
        var labelTime: TextView
        var unreadMark: ImageView

        init {
            imgPhoto = itemView.findViewById(R.id.img_avatar)
            labelPeer = itemView.findViewById(R.id.peer_number)
            labelResult = itemView.findViewById(R.id.result)
            imgReason = itemView.findViewById(R.id.result_icon)
            labelTime = itemView.findViewById(R.id.time)
            unreadMark = itemView.findViewById(R.id.unread_mark)
        }
    }
}
