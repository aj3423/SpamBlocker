package spam.blocker.ui.setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Add
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.AddAll
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.RemoveAt
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Clear
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Sort
import il.co.theblitz.observablecollections.enums.ObservableCollectionsAction.Set

import il.co.theblitz.observablecollections.lists.ObservableArrayList
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.AppInfo
import spam.blocker.util.Util.Companion.listApps

class PopupAppListFragment(
    private var selected: ObservableArrayList<String>
) : DialogFragment() {

    private var filtered = ObservableArrayList<AppInfo>()

    private lateinit var adapter: PopupAppListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.popup_dialog_select_apps, container, false)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // widgets
        val searchField = view.findViewById<SearchView>(R.id.search_package)
        val recyclerApps = view.findViewById<RecyclerView>(R.id.popup_app_list)

        adapter = PopupAppListAdapter(selected, filtered)
        val layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        recyclerApps.setLayoutManager(layoutManager)
        recyclerApps.setAdapter(adapter)

        filtered.observe(viewLifecycleOwner) {
            when (it.action) {
                Add -> adapter.notifyItemInserted(it.actionInt!!)
                AddAll -> adapter.notifyDataSetChanged()
                Set -> adapter.notifyItemChanged(it.actionInt!!)
                Clear -> adapter.notifyDataSetChanged()
                RemoveAt -> adapter.notifyItemRemoved(it.actionInt!!)
                Sort -> adapter.notifyDataSetChanged()
                else -> {}
            }
        }

        val ctx = requireContext()
        fun filterAppsByInput(): List<AppInfo> {
            // 1. filter by input
            var all = listApps(ctx)
            val input = searchField.query.toString().lowercase()

            Log.d(Def.TAG, "input is: $input")
            if (input != "") {
                all = all.filter {
                    it.pkgName.lowercase().contains(input) || it.label.lowercase().contains(input)
                }
            }
            // 2. sort by selected and package label
            val ret = all.sortedWith(compareBy<AppInfo> {
                !selected.contains(it.pkgName)
            }.thenBy {
                it.label
            })
            return ret
        }

        fun asyncRefreshAppsByInput() {
            GlobalScope.launch(Dispatchers.IO) {
                val apps = filterAppsByInput()
                withContext(Dispatchers.Main) {
                    filtered.clear()
                    filtered.addAll(apps)
                }
            }
        }

        asyncRefreshAppsByInput()

        // setup events
        searchField.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                asyncRefreshAppsByInput()
                return false
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()

        filtered.removeObservers(viewLifecycleOwner)
    }
}