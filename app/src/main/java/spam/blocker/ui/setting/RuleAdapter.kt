package spam.blocker.ui.setting

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.db.PatternRule
import spam.blocker.R
import spam.blocker.def.Def

class RuleAdapter(
    private val ctx: Context,
    private val onItemClick: (PatternRule) -> Unit,
    private val onItemLongClick: (PatternRule) -> Unit,
    private val filters: ObservableArrayList<PatternRule>,
    private val forType: Int
) : RecyclerView.Adapter<RuleAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.rule, parent, false);

        return Holder(view);
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val f = filters[position]

        holder.itemView.setOnClickListener{
            onItemClick(f)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(f)
            true
        }

        holder.labelPattern.text = f.patternStrColorful(ctx)
        holder.labelDesc.text = f.description
        holder.chkApplyToCall.isChecked = f.isForCall()
        holder.chkApplyToSms.isChecked = f.isForSms()
        if (forType != Def.ForCall) {
            holder.chkApplyToCall.visibility = View.GONE
        }

        holder.labelPriority.text = ctx.resources.getString(R.string.priority) + ": ${f.priority}"

        holder.imgBellRinging.visibility = if(f.isBlacklist && f.importance >= NotificationManager.IMPORTANCE_DEFAULT) View.VISIBLE else View.GONE
        holder.imgHeadsUp.visibility = if(f.isBlacklist && f.importance == NotificationManager.IMPORTANCE_HIGH) View.VISIBLE else View.GONE
        holder.imgShade.visibility = if(f.isBlacklist && f.importance == NotificationManager.IMPORTANCE_MIN) View.VISIBLE else View.GONE
        holder.imgStatusbarShadde.visibility = if(f.isBlacklist && f.importance >= NotificationManager.IMPORTANCE_LOW) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = filters.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var labelPattern: TextView
        var labelDesc: TextView
        var chkApplyToCall: CheckedTextView
        var chkApplyToSms: CheckedTextView
        var labelPriority: TextView
        var imgBellRinging: ImageView
        var imgHeadsUp: ImageView
        var imgShade: ImageView
        var imgStatusbarShadde: ImageView

        init {
            labelPattern = itemView.findViewById(R.id.text_filter_pattern)
            labelDesc = itemView.findViewById(R.id.text_filter_desc)
            chkApplyToCall = itemView.findViewById(R.id.chk_applied_to_call)
            chkApplyToSms = itemView.findViewById(R.id.chk_applied_to_sms)
            labelPriority = itemView.findViewById(R.id.label_priority)
            imgBellRinging = itemView.findViewById(R.id.img_bell_ringing)
            imgHeadsUp = itemView.findViewById(R.id.img_heads_up)
            imgShade = itemView.findViewById(R.id.img_shade)
            imgStatusbarShadde = itemView.findViewById(R.id.img_statusbar_shade)
        }
    }
}
