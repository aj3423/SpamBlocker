package spam.blocker.ui.history

import android.annotation.SuppressLint
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
import spam.blocker.db.RecordTable
import spam.blocker.db.SmsTable
import spam.blocker.def.Def

open class HistoryFragment<bindingT : ViewBinding>(
    private val inflateMethod: (LayoutInflater, ViewGroup?, Boolean) -> bindingT,
    protected val table: RecordTable
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

        _binding = inflateMethod.invoke(inflater, container, false)
        val root: View = binding!!.root


        recycler = root.findViewById(R.id.recycler_history)
        val layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        recycler.setLayoutManager(layoutManager)

        adapter = HistoryAdapter(requireContext(), table, viewModel.records)
        recycler.setAdapter(adapter)


        viewModel.records.observe(viewLifecycleOwner) {
            Log.d(Def.TAG, "action in Fragment: " + it.action.toString())

            when (it.action) {
                Add -> adapter.notifyItemInserted(it.actionInt!!)
                AddAll -> adapter.notifyDataSetChanged()
                Set -> adapter.notifyItemChanged(it.actionInt!!)
                Clear -> adapter.notifyDataSetChanged()
                RemoveAt -> adapter.notifyItemRemoved(it.actionInt!!)

                else -> {}
            }
        }


        val swipeLeftCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                rv: RecyclerView,
                h: RecyclerView.ViewHolder,
                t: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

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


        val fab = root.findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            table.deleteAll(ctx)
            asyncReloadFromDb()
        }
        // hide the fab when scrolling
        recycler.setOnScrollChangeListener { _, _, _, _, oldScrollY ->
            if (oldScrollY < 0) fab.hide() else fab.show()
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
            val records = table.listRecords(requireContext())
            withContext(Dispatchers.Main) {
                viewModel.records.clear()
                viewModel.records.addAll(records)
                Log.d(Def.TAG, "loaded ${viewModel.records.size} records from db")
            }
        }
    }
}

class CallFragment() : HistoryFragment<CallFragmentBinding>(
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

class SmsFragment() : HistoryFragment<SmsFragmentBinding>(
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