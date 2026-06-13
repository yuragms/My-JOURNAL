package com.s24vision.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                onOpenRecordings = { nav.navigate("recordings") },
                onOpenProfiles = { nav.navigate("profiles") },
            )
        }
        composable("recordings") {
            RecordingsScreen(onTrain = { encoded -> nav.navigate("train?video=$encoded") })
        }
        composable(
            route = "train?video={video}",
            arguments = listOf(navArgument("video") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            val encoded = entry.arguments?.getString("video")
            val path = encoded?.let { URLDecoder.decode(it, "UTF-8") }
            TrainScreen(videoPath = path)
        }
        composable("profiles") { ProfilesScreen() }
    }
}
