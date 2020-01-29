package com.taghavi.todolist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val LOG_TAG = MainActivity::class.java.simpleName

    companion object {
        private const val RC_SIGN_IN = 123
    }

    private lateinit var db: FirebaseFirestore

    private var userId: String? = null

    private val providers = arrayListOf(
        AuthUI.IdpConfig.EmailBuilder().build()
    )

    private lateinit var todoAdapter: ArrayAdapter<String>

    private val todoItems: ArrayList<String> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        referenceView()
        initFirebase()
    }

    private fun referenceView() {
        addItemButton.setOnClickListener {
            insertItem(addItemEditText.text.toString())
            addItemEditText.text.clear()
        }

        initList()
    }

    private fun insertItem(newItem: String) {
        todoItems.add(newItem)
        todoAdapter.notifyDataSetChanged()

        try {
            val userMap: HashMap<String, Any> = HashMap()
            userMap["todoList"] = todoItems
            db.collection("users").document(userId!!).set(userMap)
        } catch (exception: Exception) {
            Toast.makeText(this, "Error: insertItem -> $exception", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addListListener() {
        try {
            db.collection("users").document(userId!!)
                .addSnapshotListener(object : EventListener<DocumentSnapshot> {
                    override fun onEvent(
                        documentSnapshot: DocumentSnapshot?,
                        e: FirebaseFirestoreException?
                    ) {
                        val newTodoItems: ArrayList<String> =
                            documentSnapshot!!.get("todoList") as ArrayList<String>
                        todoItems.clear()
                        todoItems.addAll(newTodoItems)
                        todoAdapter.notifyDataSetChanged()
                    }
                })
        } catch (exception: Exception) {
            Toast.makeText(this, "Error: addListListener -> $exception", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initFirebase() {
        db = FirebaseFirestore.getInstance()
        userId = FirebaseAuth.getInstance().uid
        Log.i(LOG_TAG, "userId: $userId")

        addListListener()

        if (userId == null) {
            startActivityForResult(
                AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .build()
                , RC_SIGN_IN
            )
        }
    }

    private fun initList() {
        todoAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, todoItems)
        listTodo.adapter = todoAdapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN && resultCode == Activity.RESULT_OK) {
            userId = FirebaseAuth.getInstance().uid
            Log.i(LOG_TAG, "New userId : $userId")
        }
    }
}
