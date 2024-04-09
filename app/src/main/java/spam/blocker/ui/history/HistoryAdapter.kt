package spam.blocker.ui.history


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.R
import spam.blocker.db.ContentFilterTable
import spam.blocker.db.Db
import spam.blocker.db.NumberFilterTable
import spam.blocker.db.PatternTable
import spam.blocker.db.Record
import spam.blocker.db.RecordTable
import spam.blocker.util.Util
import spam.blocker.util.Util.Companion.getAppsMap


class HistoryAdapter(
    context: Context,
    private var table: RecordTable,
    private var records: ObservableArrayList<Record>
) :
    RecyclerView.Adapter<HistoryAdapter.Holder>()
{
    private var ctx: Context = context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.record, parent, false);

        return Holder(view);
    }

    private fun _filterReasonStr(filterTable: PatternTable, reason: String) : String {
        val f = filterTable.findPatternFilterById(ctx, reason.toLong())

        val reasonStr = if (f != null) {
            if (f.description != "") f.description else f.pattern
        } else {
            ctx.resources.getString(R.string.deleted_filter)
        }
        return reasonStr
    }
    private fun _resultStr(rec: Record): String {
        return when (rec.result) {
            Db.RESULT_ALLOWED_AS_CONTACT ->  ctx.resources.getString(R.string.contact)
            Db.RESULT_ALLOWED_BY_RECENT_APP ->  ctx.resources.getString(R.string.recent_app) + ": "
            Db.RESULT_ALLOWED_BY_REPEATED_CALL ->  ctx.resources.getString(R.string.repeated_call)
            Db.RESULT_ALLOWED_WHITELIST ->  ctx.resources.getString(R.string.whitelist) + ": " + _filterReasonStr(NumberFilterTable(), rec.reason)
            Db.RESULT_BLOCKED_BLACKLIST ->  ctx.resources.getString(R.string.blacklist) + ": " + _filterReasonStr(NumberFilterTable(), rec.reason)
            Db.RESULT_ALLOWED_BY_CONTENT ->  ctx.resources.getString(R.string.content) + ": " + _filterReasonStr(ContentFilterTable(), rec.reason)
            Db.RESULT_BLOCKED_BY_CONTENT ->  ctx.resources.getString(R.string.content) + ": " + _filterReasonStr(ContentFilterTable(), rec.reason)


            else -> ctx.resources.getString(R.string.pass)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val green = ctx.resources.getColor(R.color.dark_sea_green, null)
        val red = ctx.resources.getColor(R.color.salmon, null)

        val record = records[position]

        val contact = Util.findContact(ctx, record.peer)
        if (contact?.icon != null) {
            Util.setRoundImage(holder.imgPhoto, contact.icon!!)
        } else {
            val drawable = holder.imgPhoto.background
            // use the hash code as color
            val color = record.peer.hashCode() or Color.parseColor("#808080") // higher contrast
            DrawableCompat.setTint(drawable, color)
        }
        holder.labelPeer.text = contact?.name ?: record.peer
        holder.labelPeer.setTextColor(if (record.isNotBlocked()) green else red)
        holder.labelResult.text = _resultStr(record)
        holder.unreadMark.visibility = if (record.read) View.INVISIBLE else View.VISIBLE
        if (record.result == Db.RESULT_ALLOWED_BY_RECENT_APP) {
            holder.imgReason.setImageDrawable(getAppsMap(ctx)[record.reason]?.icon)
        }

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
        }
        // time
        if (Util.isToday(record.time)) {
            holder.labelTime.text = Util.hourMin(record.time)
        } else if (Util.isWithinAWeek(record.time)) {
            holder.labelTime.text = Util.getDayOfWeek(record.time) + "\n" + Util.hourMin(record.time)
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
