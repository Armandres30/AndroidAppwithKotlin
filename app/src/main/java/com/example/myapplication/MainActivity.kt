package com.example.myapplication

import android.graphics.pdf.models.ListItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent

import androidx.compose.runtime.mutableStateListOf


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen() // Call the new composable function
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Android")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = Modifier.fillMaxSize()
            .padding(50.dp)
    )
}

@Composable
fun ListItem(name: String) {
    Card(
        modifier = Modifier.fillMaxSize()
            .padding(12.dp)
    ) {
        Row {
            Image(
                painter = painterResource(id = R.drawable.baseline_web_24),
                contentDescription = "Photo of Person",
                modifier = Modifier.width(100.dp)
                    .height(100.dp)
            )
            Text(
                text = "URL: " + name,
                modifier = Modifier.padding(24.dp)
            )
        }

    }

}

@Composable
fun InputBox(onUrlAdded: (String) -> Unit,modifier: Modifier = Modifier) {
    var inputQuery by remember { mutableStateOf("") }
    TextField(
        value = inputQuery,
        onValueChange = {
            inputQuery = it
        },
        singleLine = true,
        modifier = Modifier
            .padding(top = 26.dp)
            .fillMaxSize()
            .onKeyEvent{ keyEvent ->
                if (keyEvent.key == Key.Enter) {
                    if(inputQuery.isNotBlank()) {
                        onUrlAdded(inputQuery) //Call the function to add URL
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
fun MainScreen() {
    //val urls = listOf("https://www.google.com", "httsp://www.duckduckgo.com")
    // Mutable state list of URLs
    val urls =
        remember { mutableStateListOf("https://www.google.com", "https://www.duckduckgo.com") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            Greeting(
                name = "User!",
                modifier = Modifier.padding(innerPadding)
            )
            InputBox(
                onUrlAdded = { newUrl ->
                    urls.plus(newUrl) // Add new URL to the list
                },
                modifier = Modifier.height(80.dp)
            )
            LazyColumn {
                items(urls) { url ->
                    ListItem(url)
                }
            }
        }
    }
}



