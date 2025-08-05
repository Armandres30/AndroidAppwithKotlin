package com.example.myapplication

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.runtime.mutableStateListOf
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.ui.graphics.Color
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.widget.Toast

// Define a key for SharedPreferences
private const val PREFS_NAME = "my_prefs"
private const val URLS_KEY = "urls"

class MainActivity : ComponentActivity() {
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // VPN Permission Granted!
            // Now you can start your VPN service.
            Log.d("MainActivity", "VPN permission granted by user.")
            startMyVpnService(this) // Call your function to start the service
        } else {
            // VPN Permission Denied.
            // Handle this gracefully - inform the user, disable VPN features, etc.
            Log.e("MainActivity", "VPN permission denied by user.")
            Toast.makeText(this, "VPN Permission Denied. Cannot start URL blocker.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen(
                    context = this,
                    onStartVpnClicked = { requestVpnPermission() }
                ) // Call the new composable function

            }
        }
    }
    private fun requestVpnPermission() {
        val vpnIntent = VpnService.prepare(this) // 'this' is MainActivity context
        if (vpnIntent != null) {
            Log.d("MainActivity", "VPN permission required. Launching system dialog.")
            vpnPermissionLauncher.launch(vpnIntent) // Now vpnPermissionLauncher is accessible
        } else {
            Log.d("MainActivity", "VPN permission already granted or not required.")
            startMyVpnService(this)
        }
    }

}

fun startMyVpnService(context: Context) {
    val intent = Intent(context, MyVpnService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
        Log.d("MainActivity", "Starting foreground service MyVpnService for Oreo+")
    } else {
        context.startService(intent)
        Log.d("MainActivity", "Starting service MyVpnService pre-Oreo")
    }
}

// Function to save the list to SharedPreferences
fun saveUrls(context: Context, urls: SnapshotStateList<String>) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = prefs.edit()
    val gson = Gson()
    val json = gson.toJson(urls)
    editor.putString(URLS_KEY, json)
    editor.apply()
}

@Composable
fun loadUrls(context: Context): SnapshotStateList<String> {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val gson = Gson()
    val json = prefs.getString(URLS_KEY, null)
    val type = object : TypeToken<List<String>>() {}.type // Change to List<String>

    // Deserialize to List<String> instead of String[]
    val urlsList: List<String> = gson.fromJson(json, type) ?: emptyList()

    return remember { mutableStateListOf(*urlsList.toTypedArray()) } // Convert List to Array
}

@Composable
fun MainScreen(context: Context, onStartVpnClicked: () -> Unit) {
    println("Welcome!")
    // Mutable state list of URLs
    val urls = loadUrls(context).ifEmpty {
        remember { mutableStateListOf("https://www.google.com", "https://www.duckduckgo.com") }
    }
    //val urls = remember { mutableStateListOf("https://www.google.com", "https://www.duckduckgo.com") }


    // This is the single source of truth for the input query
    var inputQuery by remember { mutableStateOf("") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Greeting(name = "User!")
            InputBox(
                value = inputQuery, // Pass the current value
                onValueChange = { newValue -> // Callback to update the value
                    inputQuery = newValue
                },
                onUrlAdded = { newUrl ->
                    urls.add(newUrl) // Add new URL to the list
                    saveUrls(context, urls) // Save immediately after adding
                    inputQuery = "" // Clear the input field in MainScreen's state
                    val intent = Intent(context, MyVpnService::class.java)
                    context.startService(intent)
                },
                modifier = Modifier.height(80.dp),
                urls = urls, // Pass the urls list
                context = context
            )
            // Add a button below the input field
            Button(
                onClick = {
                    if (inputQuery.isNotBlank() && inputQuery !in urls) {
                        urls.add(inputQuery)
                        saveUrls(context, urls)
                        inputQuery = "" // Clear the input field
                        onStartVpnClicked()

                    } else if (inputQuery.isNotBlank() && inputQuery in urls) {
                        // Optionally handle the case where the URL is already present
                        // and the button is clicked, maybe just clear the input.
                        inputQuery = ""
                    } else {
                        // If input is blank and button is clicked, just save current URLs
                        saveUrls(context, urls)
                        val intent = Intent(context, MyVpnService::class.java)
                        context.startService(intent)
                    }
                },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            ) {
                Text(text = "Save URLs")
            }

            // Make the LazyColumn scrollable
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(urls) { url ->
                    ListItem(url)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!\n Input your URLs to be blocked below.", color = Color.Red, //fontSize = 30.dp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(50.dp)

    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        // MainScreen(context = LocalContext.current) // For previewing MainScreen
        Greeting("Android")
    }
}

@Composable
fun InputBox(
    value: String, // Receive the current value
    onValueChange: (String) -> Unit, // Callback to notify parent of value change
    onUrlAdded: (String) -> Unit,
    modifier: Modifier = Modifier,
    urls: SnapshotStateList<String>, // Keep this if InputBox needs to know about existing URLs for validation
    context: Context
) {
    // No local state for inputQuery here anymore
    TextField(
        value = value, // Use the passed-in value
        onValueChange = onValueChange, // Call the callback when the text changes
        singleLine = true,
        modifier = modifier
            .padding(top = 26.dp)
            .fillMaxWidth()
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Enter) {
                    if (value.isNotBlank() && value !in urls) { // Use 'value' here
                        onUrlAdded(value) // Call the function to add URL (value is already up-to-date)
                        // The clearing of the input is handled by MainScreen's onUrlAdded logic
                    }
                    true
                } else {
                    false
                }
            }
    )
}


@Composable
fun ListItem(url: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row {
            Image(
                painter = painterResource(id = R.drawable.baseline_web_24),
                contentDescription = "Web Icon",
                modifier = Modifier
                    .width(100.dp)
                    .height(100.dp)
            )
            Text(
                text = "URL: $url",
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
