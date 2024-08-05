package spam.blocker.ui.history

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import spam.blocker.R
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.viewbinding.ViewBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Add
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.AddAll
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Clear
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.RemoveAt
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Set
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.databinding.CallFragmentBinding
import spam.blocker.databinding.SmsFragmentBinding
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.ui.util.Check
import spam.blocker.ui.util.SwipeCallback
import spam.blocker.ui.util.dynamicPopupMenu
import spam.blocker.util.SharedPref.Global

open class HistoryFragment<bindingT : ViewBinding>(
    private val inflateMethod: (LayoutInflater, ViewGroup?, Boolean) -> bindingT,
    protected val table: HistoryTable
) : Fragment() {

    private var _binding: bindingT? = null

    private val binding get() = _binding

    lateinit var recycler: RecyclerView

    private lateinit var adapter: HistoryAdapter

    protected lateinit var viewModel: HistoryViewModel


    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val spf = Global(ctx)

        _binding = inflateMethod.invoke(inflater, container, false)
        val root: View = binding!!.root

        recycler = root.findViewById(R.id.recycler_history)
        val layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        recycler.setLayoutManager(layoutManager)

        adapter = HistoryAdapter(this, table, viewModel.records)
        recycler.setAdapter(adapter)


        viewModel.records.observe(viewLifecycleOwner) {
            when (it.action) {
                Add -> adapter.notifyItemInserted(it.actionInt!!)
                AddAll -> adapter.notifyDataSetChanged()
                Set -> adapter.notifyItemChanged(it.actionInt!!)
                Clear -> adapter.notifyDataSetChanged()
                RemoveAt -> adapter.notifyItemRemoved(it.actionInt!!)

                else -> {}
            }
        }


        val swipeLeftCallback = object : SwipeCallback(ctx) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 1. delete from memory
                val i = viewHolder.absoluteAdapterPosition
                val recToDel = viewModel.records[i]
                viewModel.records.removeAt(i)

                // 2. delete from db
                table.deleteRecord(ctx, recToDel.id)

                // 3. show snackbar
                Snackbar.make(recycler, recToDel.peer, Snackbar.LENGTH_LONG).setAction(
                    resources.getString(R.string.undelete),
                ) {
                    table.addRecordWithId(ctx, recToDel)
                    viewModel.records.add(i, recToDel)
                }.show()
            }
        }
        ItemTouchHelper(swipeLeftCallback).attachToRecyclerView(recycler)

        val fab_display_filter = root.findViewById<FloatingActionButton>(R.id.fab_display_filter)
        fab_display_filter.setOnClickListener {
            val items = ctx.resources.getStringArray(R.array.history_display_filter).asList()
            dynamicPopupMenu(ctx, fab_display_filter, items.mapIndexed { index, label ->
                Check(label, if(index == 0)
                    spf.getShowPassed() else spf.getShowBlocked())
            }, onItemCheck = { clickedIndex, isChecked ->
                when (clickedIndex) {
                    0 -> spf.setShowPassed(isChecked)
                    1 -> spf.setShowBlocked(isChecked)
                }
                asyncReloadFromDb()
            })
        }

        val fab_clear = root.findViewById<FloatingActionButton>(R.id.fab_clear)
        fab_clear.setOnClickListener {
            table.deleteAll(ctx)
            asyncReloadFromDb()
        }
        // hide the fab when scrolling
        recycler.setOnScrollChangeListener { _, _, _, _, oldScrollY ->
            if (oldScrollY < 0) {
                fab_display_filter.hide()
                fab_clear.hide()
            } else {
                fab_display_filter.show()
                fab_clear.show()
            }
        }

        return root
    }

    override fun onDestroyView() {
        Log.d(Def.TAG, "on Fragment destroyed")
        super.onDestroyView()
        _binding = null
        viewModel.records.removeObservers(viewLifecycleOwner)
    }

    override fun onStart() {
        super.onStart()

        asyncReloadFromDb()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun asyncReloadFromDb() {
        GlobalScope.launch(Dispatchers.IO) {
            val spf = Global(requireContext())
            val showPassed = spf.getShowPassed()
            val showBlocked = spf.getShowBlocked()

            val records = table.listRecords(requireContext()).filter {
                (showPassed && it.isNotBlocked()) || (showBlocked && it.isBlocked())
            }
            withContext(Dispatchers.Main) {
                viewModel.records.clear()
                viewModel.records.addAll(records)
                Log.d(Def.TAG, "loaded ${viewModel.records.size} records from db")
            }
        }
    }
}

class CallFragment : HistoryFragment<CallFragmentBinding>(
    CallFragmentBinding::inflate,
    CallTable()
) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[CallViewModel::class.java]
        return super.onCreateView(inflater, container, savedInstanceState)
    }
}

class SmsFragment : HistoryFragment<SmsFragmentBinding>(
    SmsFragmentBinding::inflate,
    SmsTable()
) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[SmsViewModel::class.java]
        return super.onCreateView(inflater, container, savedInstanceState)
    }
}