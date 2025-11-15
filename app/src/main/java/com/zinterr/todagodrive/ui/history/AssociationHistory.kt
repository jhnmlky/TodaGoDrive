package com.zinterr.todagodrive.ui.history

import android.annotation.SuppressLint
import android.os.*
import androidx.fragment.app.Fragment
import android.view.*
import androidx.core.graphics.toColorInt
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.database.*
import com.zinterr.todagodrive.R
import com.zinterr.todagodrive.adapter.HistoryAdapter
import com.zinterr.todagodrive.databinding.AssociationHistoryBinding
import com.zinterr.todagodrive.model.*
import com.zinterr.todagodrive.ui.home.Home
import com.zinterr.todagodrive.util.*
import com.zinterr.todagodrive.viewholder.HistoryViewHolder
import com.zinterr.todagodrive.viewmodel.CurrentViewViewModel
import kotlin.getValue

@SuppressLint("SetTextI18n")
class AssociationHistory : Fragment(), HistoryClickListener {

    private lateinit var binding: AssociationHistoryBinding
    private val currentViewViewModel: CurrentViewViewModel by activityViewModels()
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var dbRef: DatabaseReference
    private lateinit var account: Account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssociationHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loading.progress.setIndicatorColor(
            "#1561FF".toColorInt(),
            "#FFBF0D3E".toColorInt())

        binding.rvHistory.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(activity)
        }

        binding.swipeRefresh.setColorSchemeResources(R.color.blue, R.color.red)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.progress)

        binding.swipeRefresh.setOnRefreshListener {
            fetchHistory()
        }
    }

    override fun onStart() {
        super.onStart()
        fetchHistory()
    }

    override fun onResume() {
        super.onResume()
        currentViewViewModel.setCurrentView(requireView())
    }

    private fun initialize() {
        account = Global.account!!
        dbRef = Firebase.database.reference
    }

    private fun fetchHistory() {
        setupProgress()
        dbRef.child("TodaGoDrive/Driver").orderByChild("assoc").equalTo(account.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val histories = mutableListOf<History>()
                    if (snapshot.exists()) {
                        for (member in snapshot.children) {
                            val driver = member.getValue(RideDriver::class.java)
                            for (child in member.children) {
                                if (child.hasChildren()) for (item in child.children) {
                                    val history = item.getValue(History::class.java)
                                    if (history != null) {
                                        history.driver = driver?.name
                                        histories.add(history)
                                    }
                                }
                            }
                        }
                    }
                    if (!histories.isEmpty()) {
                        histories.sortByDescending { it.dateTime }
                        setupHistory(ArrayList(histories))
                    } else setupHistory()
                    endProgress()
                    binding.swipeRefresh.isRefreshing = false
                }
                override fun onCancelled(error: DatabaseError) {
                    endProgress()
                    snackBar(view, "Error: ${error.message}")
                }
            })
    }

    private fun setupHistory(history: ArrayList<History>? = null) {
        if (history != null) {
            historyAdapter = HistoryAdapter(history, this, true)
            binding.rvHistory.adapter = historyAdapter
        } else {
            binding.noHistory.visibility = View.VISIBLE
            binding.rvHistory.visibility = View.GONE
        }
    }

    override fun getViewHolder(id: String): List<HistoryViewHolder> {
        return historyAdapter.getHistoryViewHolders(id)
    }

    private fun setupProgress() {
        binding.loading.message.text = "Loading data"
        binding.loading.container.visibility = View.VISIBLE
        Home().addCallbackFalse()
    }

    private fun endProgress() {
        binding.loading.container.visibility = View.GONE
        Home().addCallbackTrue()
    }
}