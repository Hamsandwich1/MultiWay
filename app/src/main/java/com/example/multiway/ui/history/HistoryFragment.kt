//Joey Teahan - 20520316
//HistoryFragment - This class is used to display the user's history of their routes.
package com.example.multiway.ui.history

//Imports that I have explained in previous classes
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

//The items that
data class HistoryItem(
    val title: String = "",
    val subtitle: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

//HistoryFragment class
class HistoryFragment : Fragment() {

    //Binding variables
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    //Connects to adapter class
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

        //
        adapter = HistoryAdapter(historyList)
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewHistory.adapter = adapter

        //Connects to firebase database
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance().getReference("History").child(userId)
        //Loads the history from the database
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyList.clear()
                //Adds the history to the list
                for (child in snapshot.children) {
                    val item = child.getValue(HistoryItem::class.java)
                    item?.let { historyList.add(it) }
                }
                //Sorts the history by newer items first
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

    //Actions that take place when the user wants to clear the history
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
