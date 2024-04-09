package spam.blocker.ui.setting

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.db.PatternFilter
import spam.blocker.R

class PatternAdapter(
    private var ctx: Context,
    private var onItemClick: (PatternFilter) -> Unit,
    private var filters: ObservableArrayList<PatternFilter>,
    private val forSmsOnly: Boolean
) : RecyclerView.Adapter<PatternAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.filter_item, parent, false);

        return Holder(view);
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val f = filters[position]

        holder.itemView.setOnClickListener{
            onItemClick(f)
        }

        val green = ctx.resources.getColor(R.color.dark_sea_green, null)
        val red = ctx.resources.getColor(R.color.salmon, null)

        holder.labelPattern.text = f.pattern
        holder.labelPattern.setTextColor(if (f.isWhitelist()) green else red)
        holder.labelDesc.text = f.description
        holder.chkApplyToCall.isChecked = f.isForCall()
        holder.chkApplyToSms.isChecked = f.isForSms()
        if (forSmsOnly) {
            holder.chkApplyToCall.visibility = View.INVISIBLE
        }

        holder.labelPriority.text = ctx.resources.getString(R.string.priority) + ": ${f.priority}"
    }

    override fun getItemCount() = filters.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var labelPattern: TextView
        var labelDesc: TextView
        var chkApplyToCall: CheckedTextView
        var chkApplyToSms: CheckedTextView
        var labelPriority: TextView

        init {
            labelPattern = itemView.findViewById(R.id.text_filter_pattern)
            labelDesc = itemView.findViewById(R.id.text_filter_desc)
            chkApplyToCall = itemView.findViewById(R.id.chk_applied_to_call)
            chkApplyToSms = itemView.findViewById(R.id.chk_applied_to_sms)
            labelPriority = itemView.findViewById(R.id.label_priority)
        }
    }
}
