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
import android.renderscript.RenderScript
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var viewModel: MyViewModel
    class MyViewModel(application: Application, private val sharedPreferences: SharedPreferences) : AndroidViewModel(application) {
        fun updatePoints(newValue: Int) {
            val editor = sharedPreferences.edit()
            editor.putInt("Points", newValue)
            editor.apply()
        }

        fun getPoints(): Int {
            return sharedPreferences.getInt("Points",0)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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

            var currentPermissionStatus by remember {
                mutableStateOf(decideCurrentPermissionStatus(locationPermissionGranted))
            }

            val scope = rememberCoroutineScope()
            val snackbarHostState = remember {
                SnackbarHostState()
            }

            val sharedPreferences = getPreferences(Context.MODE_PRIVATE)
            viewModel = MyViewModel(application, sharedPreferences)
            val containsDate = sharedPreferences.contains("Date")
            val containsPoints = sharedPreferences.contains("Points")
            if (containsDate && containsPoints) {
                MyApplicationTheme {
                    GameApp(viewModel)
                }
            }
            else {
                val editor = sharedPreferences.edit()
                editor.putInt("Date", 5)
                editor.putInt("Points", 0)
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
fun WelcomeScreen(
    onNextButtonClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
    ) {
        Text(
            text = "PlaceHolder",
            textAlign = TextAlign.Center
        )
        Button(onClick = { onNextButtonClicked() }) {
            Text(text = "Next")
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CurrentLocationContent(usePreciseLocation: Boolean, viewModel: MainActivity.MyViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var locationInfo by remember {
        mutableStateOf("")
    }

    var latitude = 0.0
    var longitude = 0.0
    val pOIList = ArrayList<LatLng>()
    pOIList.add(LatLng(42.08841350996699, -75.9697032716066))
    pOIList.add(LatLng(42.087083843469486, -75.96695668960858))
    pOIList.add(LatLng(42.091614149826036, -75.96495039726581))
    pOIList.add(LatLng(42.088731988838745, -75.96392042919864))
    pOIList.add(LatLng(42.0875559, -75.9689347))

    Greeting(latitude = 42.0893, longitude = -75.9699, pOIList)
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = {
                // getting last known location is faster and minimizes battery usage
                // This information may be out of date.
                // Location may be null as previously no client has access location
                // or location turned of in device setting.
                // Please handle for null case as well as additional check can be added before using the method
                scope.launch(Dispatchers.IO) {
                    val result = locationClient.lastLocation.await()
                    locationInfo = if (result == null) {
                        "No last known location. Try fetching the current location first"
                    } else {
                        "Current location is \n" + "lat : ${result.latitude}\n" +
                                "long : ${result.longitude}\n" + "fetched at ${System.currentTimeMillis()}"
                    }
                }
            },
        ) {
            Text("Get last known location")
        }

        Button(
            onClick = {
                viewModel.updatePoints(10)
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
                        val overlapRes = OverlapCheck(LatLng(fetchedLocation.latitude, fetchedLocation.longitude), pOIList)
                        if (overlapRes) {
                            locationInfo =
                                "${viewModel.getPoints()}"
                        }
                        else {
                            locationInfo =
                                "Current location is \n" + "lat : ${fetchedLocation.latitude}\n" +
                                        "long : ${fetchedLocation.longitude}\n" + "fetched at ${System.currentTimeMillis()}"
                        }
                    }
                }
            },
        ) {
            Text(text = "Get current location")
        }
        Text(
            text = locationInfo,
        )
    }
}

fun OverlapCheck(location: LatLng, pOIList: ArrayList<LatLng>): Boolean {
    for (element in pOIList)
    {
        if (element.latitude + 0.00025 > location.latitude && location.latitude > element.latitude - 0.00025 && element.longitude + 0.00025 > location.longitude && location.longitude > element.longitude - 0.00025)
        {
            return true
        }
    }
    return false
}

@Composable
fun Greeting(latitude: Double, longitude: Double, pOIList: ArrayList<LatLng>, modifier: Modifier = Modifier) {
    val example = LatLng(latitude, longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(example, 15f)
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = true)
    ) {
        for (LatLng in pOIList)
        {
            Marker(state = MarkerState(LatLng))
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