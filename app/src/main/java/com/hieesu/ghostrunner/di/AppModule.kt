package com.hieesu.ghostrunner.di

import com.hieesu.ghostrunner.domain.engine.CoordinateTranslator
import com.hieesu.ghostrunner.domain.engine.PathScaler
import com.hieesu.ghostrunner.domain.engine.RoadSnapEngine
import com.hieesu.ghostrunner.domain.engine.RouteGenerator
import com.hieesu.ghostrunner.domain.engine.TextToPathEngine
import com.hieesu.ghostrunner.domain.simulation.GpsJitterGenerator
import com.hieesu.ghostrunner.domain.simulation.PaceSimulator
import com.hieesu.ghostrunner.domain.simulation.RouteInterpolator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing domain-layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideTextToPathEngine(): TextToPathEngine = TextToPathEngine()

    @Provides
    @Singleton
    fun providePathScaler(): PathScaler = PathScaler()

    @Provides
    @Singleton
    fun provideCoordinateTranslator(): CoordinateTranslator = CoordinateTranslator()

    @Provides
    @Singleton
    fun provideRoadSnapEngine(): RoadSnapEngine = RoadSnapEngine()

    @Provides
    @Singleton
    fun provideRouteInterpolator(): RouteInterpolator = RouteInterpolator()

    @Provides
    @Singleton
    fun provideGpsJitterGenerator(): GpsJitterGenerator = GpsJitterGenerator()

    @Provides
    @Singleton
    fun providePaceSimulator(): PaceSimulator = PaceSimulator()

    @Provides
    @Singleton
    fun provideRouteGenerator(
        textToPathEngine: TextToPathEngine,
        pathScaler: PathScaler,
        coordinateTranslator: CoordinateTranslator,
        roadSnapEngine: RoadSnapEngine,
        routeInterpolator: RouteInterpolator,
        gpsJitterGenerator: GpsJitterGenerator,
        paceSimulator: PaceSimulator
    ): RouteGenerator = RouteGenerator(
        textToPathEngine,
        pathScaler,
        coordinateTranslator,
        roadSnapEngine,
        routeInterpolator,
        gpsJitterGenerator,
        paceSimulator
    )
}
