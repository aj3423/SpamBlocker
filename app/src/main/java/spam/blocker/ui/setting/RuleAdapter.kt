package spam.blocker.ui.setting

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.graphics.PorterDuff
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import il.co.theblitz.observablecollections.lists.ObservableArrayList
import spam.blocker.db.PatternRule
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.ui.util.UI.Companion.showIf

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

    @SuppressLint("SetTextI18n", "UseCompatLoadingForDrawables")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val f = filters[position]

        val gray = ctx.getColor(R.color.text_grey)
        val teal = ctx.getColor(R.color.teal_200)

        holder.itemView.setOnClickListener{
            onItemClick(f)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(f)
            true
        }

        holder.labelPattern.text = f.patternStrColorful(ctx, forType)
        holder.labelDesc.text = f.description
        showIf(holder.labelDesc, f.description.isNotEmpty())
        holder.imgApplyToCall.setColorFilter(if (f.isForCall()) teal else gray, PorterDuff.Mode.SRC_IN)
        holder.imgApplyToSms.setColorFilter(if (f.isForSms()) teal else gray, PorterDuff.Mode.SRC_IN)

        holder.imgBlockType.setImageDrawable(when(f.blockType) {
            Def.BLOCK_TYPE_SILENCE -> ctx.resources.getDrawable(R.drawable.ic_call_miss, null)
            Def.BLOCK_TYPE_ANSWER_AND_HANG -> ctx.resources.getDrawable(R.drawable.ic_hang, null)
            else -> ctx.resources.getDrawable(R.drawable.ic_call_blocked, null)
        })
        showIf(holder.imgForNumber, forType == Def.ForQuickCopy && f.flags.has(Def.FLAG_FOR_NUMBER))
        showIf(holder.imgForContent, forType == Def.ForQuickCopy && f.flags.has(Def.FLAG_FOR_CONTENT))
        showIf(holder.imgBlockType, f.isBlacklist && forType == Def.ForNumber)
        showIf(holder.imgApplyToCall, forType != Def.ForSms)

        holder.labelPriority.text = ctx.resources.getString(R.string.priority) + ": ${f.priority}"

        showIf(holder.imgBellRinging, f.isBlacklist && f.importance >= NotificationManager.IMPORTANCE_DEFAULT)
        showIf(holder.imgHeadsUp, f.isBlacklist && f.importance == NotificationManager.IMPORTANCE_HIGH)
        showIf(holder.imgShade, f.isBlacklist && f.importance == NotificationManager.IMPORTANCE_MIN)
        showIf(holder.imgStatusbarShadde, f.isBlacklist && f.importance >= NotificationManager.IMPORTANCE_LOW)
    }

    override fun getItemCount() = filters.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var labelPattern: TextView
        var labelDesc: TextView
        var imgForNumber: ImageView
        var imgForContent: ImageView
        var imgBlockType: ImageView
        var imgApplyToCall: ImageView
        var imgApplyToSms: ImageView
        var labelPriority: TextView
        var imgBellRinging: ImageView
        var imgHeadsUp: ImageView
        var imgShade: ImageView
        var imgStatusbarShadde: ImageView

        init {
            labelPattern = itemView.findViewById(R.id.text_filter_pattern)
            labelDesc = itemView.findViewById(R.id.text_filter_desc)
            imgForNumber = itemView.findViewById(R.id.img_for_number)
            imgForContent = itemView.findViewById(R.id.img_for_content)
            imgBlockType = itemView.findViewById(R.id.img_block_type)
            imgApplyToCall = itemView.findViewById(R.id.img_for_call)
            imgApplyToSms = itemView.findViewById(R.id.img_for_sms)
            labelPriority = itemView.findViewById(R.id.label_priority)
            imgBellRinging = itemView.findViewById(R.id.img_bell_ringing)
            imgHeadsUp = itemView.findViewById(R.id.img_heads_up)
            imgShade = itemView.findViewById(R.id.img_shade)
            imgStatusbarShadde = itemView.findViewById(R.id.img_statusbar_shade)
        }
    }
}
