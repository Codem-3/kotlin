/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.generators

import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.backend.*
import org.jetbrains.kotlin.fir.backend.utils.ConversionTypeOrigin
import org.jetbrains.kotlin.fir.backend.utils.convertWithOffsets
import org.jetbrains.kotlin.fir.backend.utils.varargElementType
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedCallableReference
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.resolve.FirSamResolver
import org.jetbrains.kotlin.fir.resolve.calls.stages.FirFakeArgumentForCallableReference
import org.jetbrains.kotlin.fir.resolve.calls.ResolvedCallArgument
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.substitution.AbstractConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.Variance

/**
 * A generator that converts callable references or arguments that needs an adapter in between. This covers:
 *   1) Suspend conversion where a reference to or qualified access of non-suspend functional type is passed as an argument whose expected
 *     type is a suspend functional type;
 *   2) coercion-to-unit where a reference to a function whose return type isn't Unit is passed as an argument whose expected return type is
 *     Unit;
 *   3) vararg spread where a reference to a function with vararg parameter is passed as an argument whose use of that vararg parameter
 *     requires spreading.
 */
class AdapterGenerator(
    private val c: Fir2IrComponents,
    private val conversionScope: Fir2IrConversionScope
) : Fir2IrComponents by c {

    private val samResolver = FirSamResolver(session, scopeSession)

    internal fun needToGenerateAdaptedCallableReference(
        callableReferenceAccess: FirCallableReferenceAccess,
        type: IrSimpleType,
        function: FirFunction
    ): Boolean {
        return needSuspendConversion(type, function) ||
                needCoercionToUnit(type, function) ||
                hasVarargOrDefaultArguments(callableReferenceAccess)
    }

    /**
     * For example,
     * fun referenceConsumer(f: suspend () -> Unit) = ...
     * fun nonSuspendFunction(...) = ...
     * fun useSite(...) = { ... referenceConsumer(::nonSuspendFunction) ... }
     *
     * At the use site, instead of referenced, we can put the suspend lambda as an adapter.
     */
    private fun needSuspendConversion(type: IrSimpleType, function: FirFunction): Boolean {
        return type.isSuspendFunction() && !function.isSuspend
    }

    /**
     * For example,
     * fun referenceConsumer(f: () -> Unit) = f()
     * fun referenced(...): Any { ... }
     * fun useSite(...) = { ... referenceConsumer(::referenced) ... }
     *
     * At the use site, instead of referenced, we can put the adapter: { ... -> referenced(...) }
     */
    private fun needCoercionToUnit(type: IrSimpleType, function: FirFunction): Boolean {
        val expectedReturnType = type.arguments.last().typeOrNull
        val actualReturnType = function.returnTypeRef.coneType
        return expectedReturnType?.isUnit() == true &&
                // In case of an external function whose return type is a type parameter, e.g., operator fun <T, R> invoke(T): R
                !actualReturnType.isUnit && actualReturnType.toSymbol(c.session) !is FirTypeParameterSymbol
    }

    /**
     * For example,
     * fun referenceConsumer(f: (Char, Char) -> String): String = ... // e.g., f(char1, char2)
     * fun referenced(vararg xs: Char) = ...
     * fun useSite(...) = { ... referenceConsumer(::referenced) ... }
     *
     * At the use site, instead of referenced, we can put the adapter: { a, b -> referenced(a, b) }
     */
    private fun hasVarargOrDefaultArguments(callableReferenceAccess: FirCallableReferenceAccess): Boolean {
        // Unbound callable reference 'A::foo'
        val calleeReference = callableReferenceAccess.calleeReference as? FirResolvedCallableReference ?: return false
        return calleeReference.mappedArguments.any { (_, value) ->
            value is ResolvedCallArgument.VarargArgument || value is ResolvedCallArgument.DefaultArgument
        }
    }

    internal fun generateAdaptedCallableReference(
        callableReferenceAccess: FirCallableReferenceAccess,
        explicitReceiverExpression: IrExpression?,
        adapteeSymbol: IrFunctionSymbol,
        type: IrSimpleType
    ): IrExpression {
        val firAdaptee = callableReferenceAccess.toResolvedCallableReference()?.resolvedSymbol?.fir as? FirFunction
        val expectedReturnType = type.arguments.last().typeOrNull
        return callableReferenceAccess.convertWithOffsets { startOffset, endOffset ->
            val boundDispatchReceiver = callableReferenceAccess.findBoundReceiver(explicitReceiverExpression, isDispatch = true)
            val boundExtensionReceiver = callableReferenceAccess.findBoundReceiver(explicitReceiverExpression, isDispatch = false)

            val irAdapterFunction = createAdapterFunctionForCallableReference(
                callableReferenceAccess, startOffset, endOffset, firAdaptee!!, type, boundDispatchReceiver, boundExtensionReceiver
            )
            val irCall = createAdapteeCallForCallableReference(
                callableReferenceAccess, firAdaptee, adapteeSymbol, irAdapterFunction, type, boundDispatchReceiver, boundExtensionReceiver
            )
            irAdapterFunction.body = IrFactoryImpl.createBlockBody(startOffset, endOffset) {
                if (expectedReturnType?.isUnit() == true) {
                    statements.add(Fir2IrImplicitCastInserter.coerceToUnitIfNeeded(irCall))
                } else {
                    statements.add(IrReturnImpl(startOffset, endOffset, builtins.nothingType, irAdapterFunction.symbol, irCall))
                }
            }

            require(irAdapterFunction.typeParameters.isEmpty()) {
                "Internal error: function adapter ${irAdapterFunction.symbol} " +
                        "has unexpected type parameters: ${irAdapterFunction.typeParameters.map { it.symbol }}\n" +
                        "They should already be used to determine exact return type and value parameters types"
            }
            val irAdapterRef = IrFunctionReferenceImpl(
                startOffset, endOffset, type, irAdapterFunction.symbol, typeArgumentsCount = 0,
                null, IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE
            ).apply {
                (boundDispatchReceiver ?: boundExtensionReceiver)?.let {
                    arguments[0] = it
                }

                reflectionTarget = adapteeSymbol
            }
            IrBlockImpl(startOffset, endOffset, type, IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE).apply {
                statements.add(irAdapterFunction)
                statements.add(irAdapterRef)
            }
        }
    }

    @Suppress("FoldInitializerAndIfToElvis")
    private fun FirCallableReferenceAccess.findBoundReceiver(
        explicitReceiverExpression: IrExpression?,
        isDispatch: Boolean
    ): IrExpression? {
        val firReceiver = if (isDispatch) dispatchReceiver else extensionReceiver
        if (firReceiver == null) {
            return null
        }
        with(callGenerator) {
            return findIrReceiver(explicitReceiverExpression, isDispatch)
        }
    }

    private fun createAdapterFunctionForCallableReference(
        callableReferenceAccess: FirCallableReferenceAccess,
        startOffset: Int,
        endOffset: Int,
        firAdaptee: FirFunction,
        type: IrSimpleType,
        boundDispatchReceiver: IrExpression?,
        boundExtensionReceiver: IrExpression?
    ): IrSimpleFunction {
        val returnType = type.arguments.last().typeOrNull!!
        val parameterTypes = type.arguments.dropLast(1).map { it.typeOrNull!! }
        val firMemberAdaptee = firAdaptee as FirMemberDeclaration
        val name = when (firAdaptee) {
            is FirConstructor -> SpecialNames.INIT
            else -> firAdaptee.symbol.name
        }
        return IrFactoryImpl.createSimpleFunction(
            startOffset = startOffset,
            endOffset = endOffset,
            origin = IrDeclarationOrigin.ADAPTER_FOR_CALLABLE_REFERENCE,
            name = name,
            visibility = DescriptorVisibilities.LOCAL,
            isInline = firMemberAdaptee.isInline,
            isExpect = false,
            returnType = returnType,
            modality = Modality.FINAL,
            symbol = IrSimpleFunctionSymbolImpl(),
            isTailrec = false,
            isSuspend = firMemberAdaptee.isSuspend || type.isSuspendFunction(),
            isOperator = firMemberAdaptee.isOperator,
            isInfix = firMemberAdaptee.isInfix,
            isExternal = false,
        ).also { irAdapterFunction ->
            irAdapterFunction.parameters = buildList {
                val boundReceiver = boundDispatchReceiver ?: boundExtensionReceiver
                if (boundReceiver != null) {
                    if (boundDispatchReceiver != null && boundExtensionReceiver != null) {
                        error("Bound callable references can't have both receivers: ${callableReferenceAccess.render()}")
                    } else {
                        this += createAdapterParameter(
                            irAdapterFunction,
                            Name.identifier("receiver"),
                            boundReceiver.type,
                            IrDeclarationOrigin.ADAPTER_PARAMETER_FOR_CALLABLE_REFERENCE,
                            IrParameterKind.ExtensionReceiver,
                        )
                    }
                }

                parameterTypes.mapIndexedTo(this) { index, parameterType ->
                    createAdapterParameter(
                        irAdapterFunction,
                        Name.identifier("p$index"),
                        parameterType,
                        IrDeclarationOrigin.ADAPTER_PARAMETER_FOR_CALLABLE_REFERENCE,
                        IrParameterKind.Regular,
                    )
                }
            }

            irAdapterFunction.parent = conversionScope.parent()!!
        }
    }

    private fun createAdapterParameter(
        adapterFunction: IrFunction,
        name: Name,
        type: IrType,
        origin: IrDeclarationOrigin,
        kind: IrParameterKind,
    ): IrValueParameter =
        IrFactoryImpl.createValueParameter(
            startOffset = adapterFunction.startOffset,
            endOffset = adapterFunction.endOffset,
            origin = origin,
            name = name,
            type = type,
            isAssignable = false,
            symbol = IrValueParameterSymbolImpl(),
            varargElementType = null,
            isCrossinline = false,
            isNoinline = false,
            isHidden = false,
            kind = kind,
        ).also { irAdapterValueParameter ->
            irAdapterValueParameter.parent = adapterFunction
        }

    private fun IrValueDeclaration.toIrGetValue(startOffset: Int, endOffset: Int): IrGetValue =
        IrGetValueImpl(startOffset, endOffset, this.type, this.symbol)

    private fun createAdapteeCallForCallableReference(
        callableReferenceAccess: FirCallableReferenceAccess,
        firAdaptee: FirFunction,
        adapteeSymbol: IrFunctionSymbol,
        adapterFunction: IrFunction,
        adaptedType: IrSimpleType,
        boundDispatchReceiver: IrExpression?,
        boundExtensionReceiver: IrExpression?
    ): IrExpression = callableReferenceAccess.convertWithOffsets { startOffset, endOffset ->
        val substitutor = callableReferenceAccess.createConeSubstitutorFromTypeArguments(session) ?: ConeSubstitutor.Empty
        val type = substitutor.substituteOrSelf(firAdaptee.returnTypeRef.coneType).toIrType()
        val irCall = when (adapteeSymbol) {
            is IrConstructorSymbol ->
                IrConstructorCallImpl.fromSymbolOwner(startOffset, endOffset, type, adapteeSymbol)
            is IrSimpleFunctionSymbol ->
                IrCallImpl(
                    startOffset,
                    endOffset,
                    type,
                    adapteeSymbol,
                    typeArgumentsCount = callableReferenceAccess.typeArguments.size,
                    origin = null,
                    superQualifierSymbol = null
                )
        }

        var adapterParameterIndex = 0
        var parameterShift = 0
        if (boundDispatchReceiver != null || boundExtensionReceiver != null) {
            val receiverParameter = adapterFunction.parameters[0]
            val receiverValue = IrGetValueImpl(
                startOffset, endOffset, receiverParameter.type, receiverParameter.symbol,
                IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE
            )
            irCall.arguments[0] = receiverValue
            parameterShift = 1
        } else if (
            callableReferenceAccess.explicitReceiver is FirResolvedQualifier &&
            (firAdaptee !is FirConstructor ||
                    firAdaptee.containingClassLookupTag()?.toRegularClassSymbol(session)?.isInner == true) &&
            ((firAdaptee as? FirMemberDeclaration)?.isStatic != true)
        ) {
            // Unbound callable reference 'A::foo'
            val adaptedReceiverParameter = adapterFunction.parameters[0]
            val adaptedReceiverValue = IrGetValueImpl(
                startOffset, endOffset, adaptedReceiverParameter.type, adaptedReceiverParameter.symbol
            )
            irCall.arguments[0] = adaptedReceiverValue
            parameterShift = 1
        }

        val mappedArguments = (callableReferenceAccess.calleeReference as? FirResolvedCallableReference)?.mappedArguments

        fun buildIrGetValueArgument(argument: FirExpression): IrGetValue {
            val parameterIndex = (argument as? FirFakeArgumentForCallableReference)?.index ?: adapterParameterIndex
            adapterParameterIndex++
            return adapterFunction.parameters[parameterIndex + parameterShift].toIrGetValue(startOffset, endOffset)
        }

        firAdaptee.valueParameters.forEachIndexed { index, valueParameter ->
            val varargElementType = valueParameter.varargElementType?.toIrType()
            val parameterType = substitutor.substituteOrSelf(valueParameter.returnTypeRef.coneType).toIrType()
            when (val mappedArgument = mappedArguments?.get(valueParameter)) {
                is ResolvedCallArgument.VarargArgument -> {
                    val valueArgument = if (mappedArgument.arguments.isEmpty()) {
                        null
                    } else {
                        val reifiedVarargElementType: IrType
                        val reifiedVarargType: IrType
                        if (varargElementType?.isTypeParameter() == true &&
                            parameterType is IrSimpleType &&
                            !parameterType.isPrimitiveArray()
                        ) {
                            reifiedVarargElementType = adaptedType.getArgumentTypeAt(index)
                            reifiedVarargType = IrSimpleTypeImpl(
                                parameterType.classifier,
                                parameterType.nullability,
                                listOf(makeTypeProjection(reifiedVarargElementType, Variance.OUT_VARIANCE)),
                                parameterType.annotations
                            )
                        } else {
                            reifiedVarargElementType = varargElementType!!
                            reifiedVarargType = parameterType
                        }

                        val adaptedValueArgument = IrVarargImpl(
                            startOffset,
                            endOffset,
                            reifiedVarargType,
                            reifiedVarargElementType
                        )
                        for (argument in mappedArgument.arguments) {
                            val irValueArgument = buildIrGetValueArgument(argument)
                            adaptedValueArgument.addElement(irValueArgument)
                        }
                        adaptedValueArgument
                    }
                    irCall.arguments[index + parameterShift] = valueArgument
                }
                ResolvedCallArgument.DefaultArgument -> {
                    irCall.arguments[index + parameterShift] = null
                }
                is ResolvedCallArgument.SimpleArgument -> {
                    val irValueArgument = buildIrGetValueArgument(mappedArgument.callArgument)
                    if (valueParameter.isVararg) {
                        irCall.arguments[index + parameterShift] =
                            IrVarargImpl(
                                startOffset, endOffset,
                                parameterType, varargElementType!!,
                                listOf(IrSpreadElementImpl(startOffset, endOffset, irValueArgument))
                            )
                    } else {
                        irCall.arguments[index + parameterShift] = irValueArgument
                    }
                }
                null -> {
                }
            }
        }

        with(callGenerator) {
            irCall.applyTypeArguments(callableReferenceAccess)
        }
    }

    internal fun IrExpression.applySamConversionIfNeeded(
        argument: FirExpression,
    ): IrExpression {
        if (argument !is FirSamConversionExpression) return this

        val samFirType = argument.resolvedType.let { it.removeExternalProjections(session.typeContext) ?: it }
        val samType = samFirType.toIrType(ConversionTypeOrigin.DEFAULT)

        // Make sure the converted IrType owner indeed has a single abstract method, since FunctionReferenceLowering relies on it.
        fun IrExpression.generateSamConversion(samType: IrType, firSamConversion: FirSamConversionExpression, samFirType: ConeKotlinType) =
            IrTypeOperatorCallImpl(
                this.startOffset, this.endOffset, samType, IrTypeOperator.SAM_CONVERSION, samType,
                castArgumentToFunctionalInterfaceForSamType(
                    argument = this,
                    argumentConeType = firSamConversion.expression.resolvedType,
                    samType = samFirType,
                    usesFunctionalTypeConversion = firSamConversion.usesFunctionKindConversion
                )
            )

        return if (this is IrBlock && (origin == IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE || origin == IrStatementOrigin.SUSPEND_CONVERSION)) {
            // The IR for adapted callable references should be
            // BLOCK ADAPTED_FUNCTION_REFERENCE(FUN ADAPTER_FOR_CALLABLE_REFERENCE, TYPE_OP SAM_CONVERSION(FUNCTION_REFERENCE))
            // Therefore, we need to insert the cast as the last statement of the block, not around the block itself.
            val lastIndex = statements.lastIndex
            val samConversion = (statements[lastIndex] as IrExpression).generateSamConversion(samType, argument, samFirType)
            statements[lastIndex] = samConversion
            this.type = samConversion.type
            this
        } else {
            generateSamConversion(samType, argument, samFirType)
        }
    }

    // See org.jetbrains.kotlin.psi2ir.generators.ArgumentsGenerationUtilsKt.castArgumentToFunctionalInterfaceForSamType (K1 counterpart)
    private fun castArgumentToFunctionalInterfaceForSamType(
        argument: IrExpression,
        argumentConeType: ConeKotlinType,
        samType: ConeKotlinType,
        usesFunctionalTypeConversion: Boolean,
    ): IrExpression {
        // The rule for SAM conversions is: the argument must be a subtype of the required function type.
        // We handle intersection types, captured types, etc. by approximating both expected and actual types.
        var approximatedConeKotlinFunctionType = getFunctionTypeForPossibleSamType(samType)?.approximateForIrOrSelf() ?: return argument

        // This line is not present in the K1 counterpart because there is InsertImplicitCasts::cast that effectively removes
        // such unnecessary casts. At the same time, many IR lowerings assume that there are no such redundant casts and many
        // tests from FirBlackBoxCodegenTestGenerated relevant to INDY start failing once this line is removed.
        val approximateArgumentConeType = argumentConeType.approximateForIrOrSelf()

        // We don't want to insert a redundant cast from a function type to a suspend function type,
        // because that's already handled by suspend conversion.
        if (usesFunctionalTypeConversion) {
            approximatedConeKotlinFunctionType = approximatedConeKotlinFunctionType.customFunctionTypeToSimpleFunctionType(session)
        }

        if (approximateArgumentConeType.isSubtypeOf(approximatedConeKotlinFunctionType, session)) {
            return argument
        }

        val irFunctionType = approximatedConeKotlinFunctionType.toIrType()
        return argument.implicitCastTo(irFunctionType)
    }

    // This function is mostly a mirror of org.jetbrains.kotlin.backend.common.SamTypeApproximator.removeExternalProjections
    // First attempts, to share the code between K1 and K2 via type contexts stumbled upon the absence of star-projection-type in K2
    // and the possibility of incorrectly mapped details that might break some code when using K1.
    private fun ConeKotlinType.removeExternalProjections(typeContext: ConeInferenceContext): ConeKotlinType? =
        when (this) {
            is ConeRigidType -> removeExternalProjections()
            is ConeFlexibleType -> mapTypesOrNull(typeContext) { it.removeExternalProjections() }
        }

    private fun ConeRigidType.removeExternalProjections(): ConeRigidType? {
        if (this@removeExternalProjections !is ConeClassLikeType) return this

        with(session.typeContext) {
            val arguments = typeArguments.ifEmpty { return this@removeExternalProjections }
            val parameters = lookupTag.getParameters()
            val parameterSet = parameters.toSet()

            @Suppress("UNCHECKED_CAST")
            val newArguments = Array(arguments.size) { i ->
                val argument = arguments[i]
                val parameter = parameters.getOrNull(i) ?: return null
                when {
                    argument.kind == ProjectionKind.IN && c.configuration.carefulApproximationOfContravariantProjectionForSam -> {
                        // Just erasing `in` from the type projection would lead to an incorrect type for the SAM adapter,
                        // and error at runtime on JVM if invokedynamic + LambdaMetafactory is used, see KT-51868.
                        // So we do it "carefully". If we have a class `A<T>` and a method that takes e.g. `A<in String>`, we check
                        // if `T` has a non-trivial upper bound. If it has one, we don't attempt to perform a SAM conversion at all.
                        // Otherwise we erase the type to `Any?`, so `A<in String>` becomes `A<Any?>`, which is the computed SAM type.
                        val upperBound = parameter.getUpperBounds().singleOrNull()?.upperBoundIfFlexible() as ConeKotlinType? ?: return null
                        if (!upperBound.isNullableAny) return null

                        upperBound
                    }
                    argument is ConeKotlinTypeProjection -> argument.type
                    else -> parameter.typeParameterSymbol.starProjectionTypeRepresentation(parameterSet)
                }
            }

            return withArguments(newArguments)
        }
    }

    // See the definition from K1 at org.jetbrains.kotlin.types.StarProjectionImpl.get_type
    // In K1, it's used more frequently because of not-nullable TypeProjection::getType, but in K2 we almost got rid of it
    // But here, we still need it to more-or-less fully reproduce the semantics of K1 when generating SAM conversions
    private fun FirTypeParameterSymbol.starProjectionTypeRepresentation(containingParameterSet: Set<ConeTypeParameterLookupTag>): ConeKotlinType {
        val substitutor = object : AbstractConeSubstitutor(session.typeContext) {
            // We don't substitute types
            override fun substituteType(type: ConeKotlinType): ConeKotlinType? = null

            override fun substituteArgument(projection: ConeTypeProjection, index: Int): ConeTypeProjection? {
                // But we substitute type parameters from the class-owner of this@FirTypeParameterSymbol as it's done in K1
                if (projection is ConeTypeParameterType && projection.lookupTag in containingParameterSet) return ConeStarProjection
                return super.substituteArgument(projection, index)
            }
        }

        return substitutor.substituteOrSelf(resolvedBounds.first().coneType)
    }

    internal fun getFunctionTypeForPossibleSamType(parameterType: ConeKotlinType): ConeKotlinType? {
        return samResolver.getSamInfoForPossibleSamType(parameterType)?.functionalType
    }

    /**
     * For example,
     * fun consumer(f: suspend () -> Unit) = ...
     * fun nonSuspendFunction = { ... }
     * fun useSite(...) = { ... consumer(nonSuspendFunction) ... }
     *
     * At the use site, instead of the argument, we can put the suspend lambda as an adapter.
     *
     * Instead of functions, a subtype of functional type can be used too:
     * class Foo {
     *   override fun invoke() = ...
     * }
     * fun useSite(...) = { ... consumer(Foo()) ... }
     */
    internal fun IrExpression.applySuspendConversionIfNeeded(
        argument: FirExpression,
        parameterType: ConeKotlinType
    ): IrExpression {
        if (this is IrBlock && origin == IrStatementOrigin.ADAPTED_FUNCTION_REFERENCE) {
            return this
        }
        // Expect the expected type to be a suspend functional type.
        if (!parameterType.isSuspendOrKSuspendFunctionType(session)) {
            return this
        }
        val expectedFunctionalType = parameterType.customFunctionTypeToSimpleFunctionType(session)

        val functionalArgumentType = calculateFunctionalArgumentType(argument)
        val invokeSymbol = findInvokeSymbol(expectedFunctionalType, functionalArgumentType) ?: return this
        val suspendConvertedType = parameterType.toIrType() as IrSimpleType
        return argument.convertWithOffsets { startOffset, endOffset ->
            val argumentType = functionalArgumentType.toIrType()
            val irAdapterFunction =
                createAdapterFunctionForArgument(startOffset, endOffset, suspendConvertedType, argumentType, invokeSymbol)
            val irAdapterRef = IrFunctionReferenceImpl(
                startOffset, endOffset, suspendConvertedType, irAdapterFunction.symbol, irAdapterFunction.typeParameters.size,
                null, IrStatementOrigin.SUSPEND_CONVERSION
            )
            IrBlockImpl(startOffset, endOffset, suspendConvertedType, IrStatementOrigin.SUSPEND_CONVERSION).apply {
                statements.add(irAdapterFunction)
                statements.add(irAdapterRef.apply { arguments[0] = this@applySuspendConversionIfNeeded })
            }
        }
    }

    private fun calculateFunctionalArgumentType(argument: FirExpression): ConeKotlinType {
        var argumentType = ((argument as? FirSamConversionExpression)?.expression ?: argument).resolvedType.fullyExpandedType()
        if (argumentType.isKProperty(session) || argumentType.isKMutableProperty(session)) {
            val functionClassId = FunctionTypeKind.Function.numberedClassId(argumentType.typeArguments.size - 1)
            argumentType = functionClassId.toLookupTag().constructClassType(typeArguments = argumentType.typeArguments)
        }
        return argumentType
    }

    /**
     * Returns the proper `invoke` symbol of a FunctionN type and the expected FunctionN argument type
     */
    private fun findInvokeSymbol(
        expectedFunctionalType: ConeClassLikeType,
        argumentType: ConeKotlinType
    ): IrSimpleFunctionSymbol? {
        val argumentTypeWithInvoke = argumentType
            .findSubtypeOfBasicFunctionType(session, expectedFunctionalType)
            ?.lowerBoundIfFlexible()
            ?: return null

        return if (argumentTypeWithInvoke.isSomeFunctionType(session)) {
            (argumentTypeWithInvoke as? ConeClassLikeType)?.findBaseInvokeSymbol(session, scopeSession)
        } else {
            argumentTypeWithInvoke.findContributedInvokeSymbol(
                session, scopeSession, expectedFunctionalType, shouldCalculateReturnTypesOfFakeOverrides = true
            )
        }?.let {
            declarationStorage.getIrFunctionSymbol(it) as? IrSimpleFunctionSymbol
        }
    }

    private fun createAdapterFunctionForArgument(
        startOffset: Int,
        endOffset: Int,
        type: IrSimpleType,
        argumentType: IrType,
        invokeSymbol: IrSimpleFunctionSymbol
    ): IrSimpleFunction {
        val returnType = type.arguments.last().typeOrFail
        val parameterTypes = type.arguments.dropLast(1).map { it.typeOrFail }
        return IrFactoryImpl.createSimpleFunction(
            startOffset = startOffset,
            endOffset = endOffset,
            origin = IrDeclarationOrigin.ADAPTER_FOR_SUSPEND_CONVERSION,
            name = Name.identifier(conversionScope.scope().inventNameForTemporary("suspendConversion")),
            visibility = DescriptorVisibilities.LOCAL,
            isInline = false,
            isExpect = false,
            returnType = returnType,
            modality = Modality.FINAL,
            symbol = IrSimpleFunctionSymbolImpl(),
            isTailrec = false,
            isSuspend = true,
            isOperator = false,
            isInfix = false,
            isExternal = false,
        ).also { irAdapterFunction ->
            irAdapterFunction.parameters = buildList {
                this += createAdapterParameter(
                    irAdapterFunction,
                    Name.identifier($$"$callee"),
                    argumentType,
                    IrDeclarationOrigin.ADAPTER_PARAMETER_FOR_SUSPEND_CONVERSION,
                    IrParameterKind.ExtensionReceiver,
                )

                parameterTypes.mapIndexedTo(this) { index, parameterType ->
                    createAdapterParameter(
                        irAdapterFunction,
                        Name.identifier("p$index"),
                        parameterType,
                        IrDeclarationOrigin.ADAPTER_PARAMETER_FOR_SUSPEND_CONVERSION,
                        IrParameterKind.Regular,
                    )
                }
            }
            irAdapterFunction.body = IrFactoryImpl.createBlockBody(startOffset, endOffset) {
                val irCall = createAdapteeCallForArgument(startOffset, endOffset, irAdapterFunction, invokeSymbol)
                if (returnType.isUnit()) {
                    statements.add(irCall)
                } else {
                    statements.add(IrReturnImpl(startOffset, endOffset, builtins.nothingType, irAdapterFunction.symbol, irCall))
                }
            }
            irAdapterFunction.parent = conversionScope.parent()!!
        }
    }

    private fun createAdapteeCallForArgument(
        startOffset: Int,
        endOffset: Int,
        adapterFunction: IrFunction,
        invokeSymbol: IrSimpleFunctionSymbol
    ): IrExpression {
        val irCall = IrCallImpl(
            startOffset, endOffset,
            adapterFunction.returnType,
            invokeSymbol,
            typeArgumentsCount = 0
        )
        for ((i, parameter) in adapterFunction.parameters.withIndex()) {
            irCall.arguments[i] = parameter.toIrGetValue(startOffset, endOffset)
        }
        return irCall
    }

    fun generateFunInterfaceConstructorReference(
        callableReference: FirCallableReferenceAccess,
        callableSymbol: FirFunctionSymbol<*>,
        irReferenceType: IrType
    ): IrExpression =
        callableReference.convertWithOffsets { startOffset: Int, endOffset: Int ->
            //  {
            //      fun <ADAPTER_FUN>(function: <FUN_TYPE>): <FUN_INTERFACE_TYPE> =
            //          <FUN_INTERFACE_TYPE>(function!!)
            //      ::<ADAPTER_FUN>
            //  }

            val irAdapterFun = generateFunInterfaceConstructorAdapter(startOffset, endOffset, callableSymbol, irReferenceType)

            val irAdapterRef = IrFunctionReferenceImpl(
                startOffset, endOffset,
                type = irReferenceType,
                symbol = irAdapterFun.symbol,
                typeArgumentsCount = irAdapterFun.typeParameters.size,
                reflectionTarget = irAdapterFun.symbol,
                origin = IrStatementOrigin.FUN_INTERFACE_CONSTRUCTOR_REFERENCE
            )

            IrBlockImpl(
                startOffset, endOffset,
                irReferenceType,
                IrStatementOrigin.FUN_INTERFACE_CONSTRUCTOR_REFERENCE,
                listOf(
                    irAdapterFun,
                    irAdapterRef
                )
            )
        }

    private fun IrSimpleType.getArgumentTypeAt(index: Int): IrType {
        val irTypeArgument = this.arguments[index] as? IrTypeProjection
            ?: throw AssertionError("Type projection expected at argument $index: ${this.render()}")
        return irTypeArgument.type
    }

    private fun generateFunInterfaceConstructorAdapter(
        startOffset: Int,
        endOffset: Int,
        callableSymbol: FirFunctionSymbol<*>,
        irReferenceType: IrType
    ): IrSimpleFunction {
        // Here irReferenceType is always kotlin.reflect.KFunction1<FUN_TYPE, FUN_INTERFACE_TYPE>
        val irSimpleReferenceType = irReferenceType as? IrSimpleType
            ?: throw AssertionError("Class type expected: ${irReferenceType.render()}")
        val irSamType = irSimpleReferenceType.getArgumentTypeAt(1)
        val irFunctionType = irSimpleReferenceType.getArgumentTypeAt(0)

        val functionParameter = callableSymbol.valueParameterSymbols.singleOrNull()
            ?: throw AssertionError("Single value parameter expected: ${callableSymbol.valueParameterSymbols}")

        return IrFactoryImpl.createSimpleFunction(
            startOffset = startOffset,
            endOffset = endOffset,
            origin = IrDeclarationOrigin.ADAPTER_FOR_FUN_INTERFACE_CONSTRUCTOR,
            name = callableSymbol.name,
            visibility = DescriptorVisibilities.LOCAL,
            isInline = false,
            isExpect = false,
            returnType = irSamType,
            modality = Modality.FINAL,
            symbol = IrSimpleFunctionSymbolImpl(),
            isTailrec = false,
            isSuspend = false,
            isOperator = false,
            isInfix = false,
            isExternal = false,
        ).also { irAdapterFunction ->
            val irFunctionParameter = createAdapterParameter(
                irAdapterFunction,
                functionParameter.name,
                irFunctionType,
                IrDeclarationOrigin.ADAPTER_PARAMETER_FOR_CALLABLE_REFERENCE,
                IrParameterKind.Regular,
            )
            irAdapterFunction.parameters = listOf(irFunctionParameter)
            irAdapterFunction.body = IrFactoryImpl.createBlockBody(
                startOffset, endOffset,
                listOf(
                    IrReturnImpl(
                        startOffset, endOffset, c.builtins.nothingType, irAdapterFunction.symbol,
                        IrTypeOperatorCallImpl(
                            startOffset, endOffset, irSamType, IrTypeOperator.SAM_CONVERSION, irSamType,
                            IrCallImplWithShape(
                                startOffset, endOffset, irFunctionType, builtins.checkNotNullSymbol,
                                typeArgumentsCount = 1,
                                valueArgumentsCount = 1,
                                contextParameterCount = 0,
                                hasDispatchReceiver = false,
                                hasExtensionReceiver = false,
                                origin = IrStatementOrigin.EXCLEXCL
                            ).apply {
                                typeArguments[0] = irFunctionType
                                arguments[0] = IrGetValueImpl(startOffset, endOffset, irFunctionParameter.symbol)
                            }
                        )
                    )
                )
            )
            irAdapterFunction.parent = conversionScope.parent()!!
        }
    }
}
