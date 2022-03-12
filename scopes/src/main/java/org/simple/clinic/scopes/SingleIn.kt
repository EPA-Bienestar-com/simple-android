package org.simple.clinic.scopes

import javax.inject.Scope
import kotlin.reflect.KClass

/**
 * Identifies a type that the injector only instantiates once in a given scope.
 */
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class SingleIn(val scope: KClass<*>)
