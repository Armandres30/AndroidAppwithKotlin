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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Cyan
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import androidx.compose.runtime.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Define a key for SharedPreferences
private const val PREFS_NAME = "my_prefs"
private const val URLS_KEY = "urls"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen(context = this) // Call the new composable function
            }
        }
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
// Function to load the list from SharedPreferences
fun loadUrls(context: Context): SnapshotStateList<String> {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val gson = Gson()
    val json = prefs.getString(URLS_KEY, null)
    val type = object : TypeToken<MutableList<String>>() {}.type
    return remember { mutableStateListOf(*gson.fromJson(json, type) ?: emptyArray()) }
}

@Composable
fun MainScreen(context: Context) {
    lateinit var sharedPreferences: SharedPreferences
    // Mutable state list of URLs
    val urls = loadUrls(context).ifEmpty {
        remember { mutableStateListOf("https://www.google.com", "https://www.duckduckgo.com") }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Greeting(name = "User!")
            InputBox(
                onUrlAdded = { newUrl ->
                    urls.add(newUrl) // Add new URL to the list
                },
                modifier = Modifier.height(80.dp),
                urls,
                context
            )
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
        Greeting("Android")
    }
}

@Composable
fun InputBox(
    onUrlAdded: (String) -> Unit,
    modifier: Modifier = Modifier,
    urls: SnapshotStateList<String>,
    context: Context
) {
    var inputQuery by remember { mutableStateOf("") }
    TextField(
        value = inputQuery,
        onValueChange = {
            inputQuery = it
        },
        singleLine = true,
        modifier = modifier
            .padding(top = 26.dp)
            .fillMaxWidth()
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Enter) {
                    if (inputQuery.isNotBlank() && inputQuery !in urls) {
                        onUrlAdded(inputQuery) // Call the function to add URL
                        saveUrls(context, urls) // Save the updated list
                        inputQuery = "" // Clear the input field
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
    //val LightBlue = Color(0xFFADD8E6) // Light Blue color
    //val Purple = Color(0xFF800080) // Purple color
    //val gradientColors = listOf(Cyan, LightBlue, Purple /*...*/)
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
                /*style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = gradientColors
                    )
                )*/
            )
        }
    }
}
