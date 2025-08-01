/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.components

import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.resolve.deprecation.DeprecationInfo

@KaExperimentalApi
@SubclassOptInRequired(KaImplementationDetail::class)
public interface KaSymbolInformationProvider : KaSessionComponent {
    /**
     * The deprecation status of the given symbol, or `null` if the declaration is not deprecated.
     */
    @KaExperimentalApi
    public val KaSymbol.deprecationStatus: DeprecationInfo?

    /**
     * Whether the function symbol meets all the requirements to be declared as an [operator function](https://kotlinlang.org/docs/operator-overloading.html).
     *
     * In Kotlin, the set of functions which can be declared as an operator is predefined. [canBeOperator] not only checks the name of a
     * potential operator function, but also its signature, depending on the operator.
     *
     * [canBeOperator] does not determine whether the function symbol *is* declared as an operator. For this purpose, use
     * [KaNamedFunctionSymbol.isOperator] instead.
     *
     * #### Example
     *
     * ```kotlin
     * class A
     *
     * fun A.plus(that: A): A = A() // canBeOperator = true, as it meets all requirements for `plus`.
     *
     * operator fun A.contains(that: A): Boolean = true // canBeOperator = true, as it's already an operator.
     *
     * fun A.something(that: A): A = A() // canBeOperator = false, as there is no operator with such a name.
     *
     * fun A.minus(): A = A() // canBeOperator = false, as `minus` is a binary operator and should have one parameter.
     * ```
     */
    @KaExperimentalApi
    public val KaNamedFunctionSymbol.canBeOperator: Boolean

    /**
     * The deprecation status of the given symbol for the given [annotation use-site target](https://kotlinlang.org/docs/annotations.html#annotation-use-site-targets),
     * or `null` if the declaration is not deprecated.
     */
    @KaExperimentalApi
    public fun KaSymbol.deprecationStatus(annotationUseSiteTarget: AnnotationUseSiteTarget?): DeprecationInfo?

    /**
     * The deprecation status of the given property getter, or `null` if the getter is not deprecated.
     */
    @KaExperimentalApi
    public val KaPropertySymbol.getterDeprecationStatus: DeprecationInfo?

    /**
     * The deprecation status of the given property setter, or `null` if the setter is not deprecated or doesn't exist.
     */
    @KaExperimentalApi
    public val KaPropertySymbol.setterDeprecationStatus: DeprecationInfo?

    /**
     * A set of applicable targets for an annotation class symbol, or `null` if the symbol is not an annotation class.
     */
    @KaExperimentalApi
    public val KaClassSymbol.annotationApplicableTargets: Set<KotlinTarget>?


    /**
     * Whether the property is an [inline property](https://kotlinlang.org/docs/inline-functions.html#inline-properties).
     * A property is considered `inline` when both of its accessors are `inline` or when it has the `inline` keyword.
     * The `inline` keyword on a property is syntactic sugar for marking both accessors as `inline`.
     */
    @KaExperimentalApi
    public val KaKotlinPropertySymbol.isInline: Boolean
}

/**
 * @see KaSymbolInformationProvider.deprecationStatus
 */
@KaContextParameterApi
@KaExperimentalApi
context(context: KaSymbolInformationProvider)
public val KaSymbol.deprecationStatus: DeprecationInfo?
    get() = with(context) { deprecationStatus }

/**
 * @see KaSymbolInformationProvider.canBeOperator
 */
@KaContextParameterApi
@KaExperimentalApi
context(context: KaSymbolInformationProvider)
public val KaNamedFunctionSymbol.canBeOperator: Boolean
    get() = with(context) { canBeOperator }

/**
 * @see KaSymbolInformationProvider.deprecationStatus
 */
@KaContextParameterApi
@KaExperimentalApi
context(context: KaSymbolInformationProvider)
public fun KaSymbol.deprecationStatus(annotationUseSiteTarget: AnnotationUseSiteTarget?): DeprecationInfo? {
    return with(context) { deprecationStatus(annotationUseSiteTarget) }
}

/**
 * @see KaSymbolInformationProvider.getterDeprecationStatus
 */
@KaContextParameterApi
@KaExperimentalApi
context(context: KaSymbolInformationProvider)
public val KaPropertySymbol.getterDeprecationStatus: DeprecationInfo?
    get() = with(context) { getterDeprecationStatus }

/**
 * @see KaSymbolInformationProvider.setterDeprecationStatus
 */
@KaContextParameterApi
@KaExperimentalApi
context(context: KaSymbolInformationProvider)
public val KaPropertySymbol.setterDeprecationStatus: DeprecationInfo?
    get() = with(context) { setterDeprecationStatus }

/**
 * @see KaSymbolInformationProvider.annotationApplicableTargets
 */
@KaContextParameterApi
@KaExperimentalApi
context(context: KaSymbolInformationProvider)
public val KaClassSymbol.annotationApplicableTargets: Set<KotlinTarget>?
    get() = with(context) { annotationApplicableTargets }

/**
 * @see KaSymbolInformationProvider.isInline
 */
@KaContextParameterApi
@KaExperimentalApi
context(context: KaSymbolInformationProvider)
public val KaKotlinPropertySymbol.isInline: Boolean
    get() = with(context) { isInline }
