package com.example.multiway.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multiway.databinding.FragmentHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import androidx.recyclerview.widget.RecyclerView

data class HistoryItem(
    val title: String = "",
    val subtitle: String = "",
    val timestamp: Long = System.currentTimeMillis()
)


class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: HistoryAdapter
    private val historyList = mutableListOf<HistoryItem>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoryAdapter(historyList)
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewHistory.adapter = adapter

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance().getReference("History").child(userId)

        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyList.clear()
                for (child in snapshot.children) {
                    val item = child.getValue(HistoryItem::class.java)
                    item?.let { historyList.add(it) }
                }
                historyList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to load history: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
        binding.clearHistoryButton.setOnClickListener {
            clearHistory()
        }

    }


    private fun clearHistory() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val historyRef = FirebaseDatabase.getInstance().getReference("History").child(userId)

        historyRef.removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to clear history", Toast.LENGTH_SHORT).show()
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



}
