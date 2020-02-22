package com.taghavi.todolist

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*


class MainActivity : AppCompatActivity(), TodoAdapter.onImageClickedListener {

    private val LOG_TAG = MainActivity::class.java.simpleName

    companion object {
        private const val RC_SIGN_IN = 123
        private const val RC_PICK_IMAGE = 321
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var remoteConfig: FirebaseRemoteConfig

    private var userId: String? = null

    private val providers = arrayListOf(
        AuthUI.IdpConfig.EmailBuilder().build()
    )

    private lateinit var todoAdapter: TodoAdapter

    private val todoItems: ArrayList<String> = ArrayList()

    var requestingPosition: Int = -1

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

        fab.setOnClickListener {
            layoutAddItem.expand()
            fab.visibility = View.GONE
            touchInterceptor.visibility = View.VISIBLE
        }

        touchInterceptor.setOnClickListener {
            layoutAddItem.collapse()
            fab.visibility = View.VISIBLE
            touchInterceptor.visibility = View.GONE
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

            logAddItemEvent(newItem)
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
        storage = FirebaseStorage.getInstance()
        userId = FirebaseAuth.getInstance().uid
        analytics = FirebaseAnalytics.getInstance(this)
        remoteConfig = FirebaseRemoteConfig.getInstance()
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        Log.i(LOG_TAG, "userId: $userId")


        if (userId == null) {
            startActivityForResult(
                AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .build()
                , RC_SIGN_IN
            )
            return
        }
        addListListener()
    }

    private fun initList() {
        todoAdapter = TodoAdapter(this, R.layout.item_todo, todoItems)
        listTodo.adapter = todoAdapter
    }

    private fun saveFile(input: InputStream, itemName: String): Uri? {
        val file = File(cacheDir, itemName)
        var returnUri: Uri? = null
        try {
            val output: OutputStream = FileOutputStream(file)
            try {
                val buffer = ByteArray(4 * 1024) // or other buffer size
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    output.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } finally {
            try {
                input.close()
                returnUri = Uri.fromFile(file)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return returnUri
    }

    private fun logAddItemEvent(itemName: String) {
        val logBundle = Bundle()
        logBundle.putString(FirebaseAnalytics.Param.ITEM_NAME, itemName)
        analytics.logEvent("AddItem", logBundle)
    }

    private fun fetchParameters() {
        val cacheExpiration: Long = 3600
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings).addOnSuccessListener { }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN && resultCode == Activity.RESULT_OK) {
            userId = FirebaseAuth.getInstance().uid
            Log.i(LOG_TAG, "New userId : $userId")
            initFirebase()
        }

        if (requestCode == RC_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val itemName = todoItems[requestingPosition]

            try {
                val inputStream = contentResolver.openInputStream(data!!.data!!)
                val fileUri = saveFile(inputStream!!, itemName)

                val imageReference =
                    storage.reference.child(userId!!).child("image").child(itemName)

                imageReference.putFile(fileUri!!).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i(LOG_TAG, "Upload success: ${task.result}")
                    } else {
                        Log.i(LOG_TAG, "Upload failed: ${task.exception}")
                    }
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }

        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.ic_logout) {
            FirebaseAuth.getInstance().signOut()
            initFirebase()
            return true
        }
        return false
    }

    override fun OnImageClicked(position: Int) {
        requestingPosition = position

        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), RC_PICK_IMAGE)
    }
}
