package com.hieesu.ghostrunner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class annotated with @HiltAndroidApp to trigger Hilt's code generation.
 */
@HiltAndroidApp
class GhostRunnerApp : Application()
