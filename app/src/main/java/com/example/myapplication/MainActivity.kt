package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.shapes.Shape
import android.icu.util.Calendar
import android.renderscript.RenderScript
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import java.text.SimpleDateFormat
import java.util.Date

val pOIList = arrayListOf<LatLng>(
    LatLng(42.08841350996699, -75.9697032716066), //bartle
    LatLng(42.087083843469486, -75.96695668960858), //union
    LatLng(42.091614149826036, -75.96495039726581), //east gym
    LatLng(42.088731988838745, -75.96392042919864), //whitney
    LatLng(42.0876609, -75.9684465) //Watson Commons (Engineering Building)
)

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var viewModel: MyViewModel
    class MyViewModel(application: Application, private val sharedPreferences: SharedPreferences) : AndroidViewModel(application) { //used to interact with shared preferences --> it is not supposed to be directly referenced from inside a composable function
        fun updatePoints(newValue: Int) {
            val editor = sharedPreferences.edit()
            editor.putInt("Points", newValue)
            editor.apply()
        }

        fun getPoints(): Int {
            return sharedPreferences.getInt("Points",0)
        }

        fun updatePOIStatus(index: Int, newValue: Boolean) {
            val editor = sharedPreferences.edit()
            editor.putBoolean("$index", newValue)
            editor.apply()
        }

        fun getPOIStatus(index: Int): Boolean {
            return sharedPreferences.getBoolean("$index", false)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            //the permissions that will necessary for the program to run
            var locationPermissionGranted by remember {mutableStateOf(checkPermission())}
            val requestedPermissions = arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
                onResult = {permissions ->
                    locationPermissionGranted = permissions.values.reduce { acc, isPermissionGranted ->
                        acc && isPermissionGranted
                    }

                    if(!locationPermissionGranted) {

                    }
                }
            )

            //checks if the location permissions have already been granted
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(key1 = lifecycleOwner, effect = {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START &&
                        !locationPermissionGranted) {
                        locationPermissionLauncher.launch(requestedPermissions)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            })

            //probably unnecessary, just keeping them for now
            var currentPermissionStatus by remember {
                mutableStateOf(decideCurrentPermissionStatus(locationPermissionGranted))
            }

            val scope = rememberCoroutineScope()
            val snackbarHostState = remember {
                SnackbarHostState()
            }

            //implementation of persistent storage using sharedpreferences api
            val sharedPreferences = getPreferences(Context.MODE_PRIVATE)
            viewModel = MyViewModel(application, sharedPreferences)
            val containsDate = sharedPreferences.contains("Date")
            val containsPoints = sharedPreferences.contains("Points")
            val calendar = Calendar.getInstance()
            val day = calendar.get(Calendar.DATE)
            if (containsDate && containsPoints) {
                if(sharedPreferences.getInt("Date", 0) != day) {
                    val currPoints = sharedPreferences.getInt("Points", 0)
                    val editor = sharedPreferences.edit()
                    editor.putInt("Points", currPoints - 5)
                    editor.putInt("Date", day)
                    editor.putBoolean("0", true)
                    editor.putBoolean("1", true)
                    editor.putBoolean("2", true)
                    editor.putBoolean("3", true)
                    editor.putBoolean("4", true)
                    editor.apply()
                }
                MyApplicationTheme {
                    GameApp(viewModel)
                }
            }
            else {
                val editor = sharedPreferences.edit()
                editor.putInt("Date", day)
                editor.putInt("Points", 10)
                editor.putBoolean("0", true)
                editor.putBoolean("1", true)
                editor.putBoolean("2", true)
                editor.putBoolean("3", true)
                editor.putBoolean("4", true)
                editor.apply()
                MyApplicationTheme {
                    TestScreen()
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun decideCurrentPermissionStatus(locationPermissionGranted: Boolean): String {
        return if (locationPermissionGranted) "Granted"
        else "Denied"
    }
}

@Composable
fun GameApp(
    viewModel: MainActivity.MyViewModel,
    navController: NavHostController = rememberNavController(),
) {
    //used to navigate between different screens
    NavHost(navController = navController, startDestination = GameScreen.Start.name, modifier = Modifier) {
        composable(route = GameScreen.Start.name) {
            WelcomeScreen(onNextButtonClicked = { navController.navigate(GameScreen.Map.name) })
        }
        composable(route = GameScreen.Map.name)
        {
            CurrentLocationContent(usePreciseLocation = true, viewModel)
        }
    }
}

@Composable
fun ClickableImageButton(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .width(250.dp)
            .height(100.dp)
            .offset(y = 16.dp)
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.Center),
        )
    }
}

@Composable
fun WelcomeScreen(
    onNextButtonClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundImage = painterResource(R.drawable.title_screen)
    val playButtonImage = painterResource(R.drawable.play_button)
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = backgroundImage,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Press Play to Conquer the Campus!",
                textAlign = TextAlign.Center,
                color = Color.White,
                // fontSize = 20.dp,

                )
            Spacer(modifier = Modifier.height(16.dp))
            ClickableImageButton(
                painter = playButtonImage,
                onClick = onNextButtonClicked,
                )
          }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CurrentLocationContent(usePreciseLocation: Boolean, viewModel: MainActivity.MyViewModel) {
    val mapScreenStyle = TextStyle(
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var locationInfo by remember {
        mutableStateOf("")
    }
    var gotLocationInfo = false
    var latitude = 0.0
    var longitude = 0.0
    var currPoints by remember {
        mutableIntStateOf(viewModel.getPoints())
    }

    Greeting(latitude = 42.0893, longitude = -75.9699, pOIList, viewModel)
    Column(
        Modifier
            .animateContentSize()
            .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(    // points display
            modifier = Modifier
                .padding(16.dp)
                .width(150.dp)
        ) {
            Text(
                text = "Points: ${viewModel.getPoints()}",
                style = mapScreenStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(23, 157, 122))
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(385.dp))

        Text(   // last known location text
            text = locationInfo,
            style = mapScreenStyle,
            fontSize = 20.sp,
            textAlign = TextAlign.Left,
            modifier = Modifier
                .padding(8.dp)
                .background(Color(23, 157, 122, 95)),

        )

        Box(    // get current location button
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Button(
                onClick = {
                    //To get more accurate or fresher device location use this method
                    scope.launch(Dispatchers.IO) {
                        val priority = if (usePreciseLocation) {
                            100
                        } else {
                            104
                        }
                        val result = locationClient.getCurrentLocation(
                            priority,
                            CancellationTokenSource().token,
                        ).await()
                        result?.let { fetchedLocation ->
                            latitude = fetchedLocation.latitude
                            longitude = fetchedLocation.longitude
                            val overlapRes = OverlapCheck(
                                LatLng(
                                    fetchedLocation.latitude,
                                    fetchedLocation.longitude
                                ), pOIList, viewModel
                            )
                            if (overlapRes) {
                                    "${viewModel.getPoints()}"
                            } else {
                                gotLocationInfo = true
                                val formattedLatitude = "%.3f".format(fetchedLocation.latitude)
                                val formattedLongitude = "%.3f".format(fetchedLocation.longitude)

                                val currentTimeMillis = System.currentTimeMillis()
                                val dateTimeFormat = SimpleDateFormat("MM/dd HH:mm")
                                val formattedTime = dateTimeFormat.format(Date(currentTimeMillis))

                                locationInfo =
                                    "Last known location: \n" +
                                    "Lat : $formattedLatitude\n" +
                                    "Long : $formattedLongitude\n" +
                                    "Synced at $formattedTime"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors (
                    containerColor = Color(23, 157, 122), // Set background color to match the text box
                    contentColor = Color.White
                    ),
            ) {
                Text(
                    text = "Sync your location\uD83D\uDCCD",
                    fontSize = 22.sp,
                    style = mapScreenStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(23, 157, 122))
                        .padding(4.dp)
                )
            }
        }
    }
}

fun OverlapCheck(location: LatLng, pOIList: ArrayList<LatLng>, viewModel: MainActivity.MyViewModel): Boolean {
    for (index in pOIList.indices)
    {
        if (pOIList[index].latitude + 0.00025 > location.latitude && location.latitude > pOIList[index].latitude - 0.00025 && pOIList[index].longitude + 0.00025 > location.longitude && location.longitude > pOIList[index].longitude - 0.00025 && viewModel.getPOIStatus(index))
        {
            var currPoints = viewModel.getPoints()
            currPoints += 5
            viewModel.updatePoints(currPoints)
            viewModel.updatePOIStatus(index, newValue = false)
            return true
        }
    }
    return false
}

@Composable
fun Greeting(latitude: Double, longitude: Double, pOIList: ArrayList<LatLng>, viewModel: MainActivity.MyViewModel, modifier: Modifier = Modifier) {
    val example = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(example, 15f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = true)
    ) {
        for (index in pOIList.indices)
        {
            if (viewModel.getPOIStatus(index))
            {
                Marker(state = MarkerState(pOIList[index]))
            }
        }
    }
}

@Composable
fun TestScreen()
{
    Text(text = "Hello World")
}

enum class GameScreen
{
    Start,
    Map,
}