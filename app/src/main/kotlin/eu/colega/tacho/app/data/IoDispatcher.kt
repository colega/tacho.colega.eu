package eu.colega.tacho.app.data

import javax.inject.Qualifier

/** Marks the IO dispatcher binding, so tests can substitute a test dispatcher. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher
