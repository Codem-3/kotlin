// LL_FIR_DIVERGENCE
//   LL test doesn't report backend diagnostics
// LL_FIR_DIVERGENCE
// RUN_PIPELINE_TILL: BACKEND
// LATEST_LV_DIFFERENCE
// IGNORE_DEXING
// ISSUE: KT-49710
// WITH_STDLIB

// FILE: JClass.java

import org.jetbrains.annotations.Nullable;

public class JClass {
    @Nullable
    public static int intProp = 0;
    @Nullable
    public static Integer integerProp = 0;
    @Nullable
    public static String stringProp = "";
    @Nullable
    public static Object objectProp = new Object();
}


// FILE: test.kt

fun Int?.isNull() = when (this) {
    null -> true
    is Int -> false
}

fun <T> List<T>.isNull() = when (this) {
    is List<T> -> false
}

fun <T> List<T>.isNull1() = when (this) {
    is List<*> -> false
}

fun <T: Int?> isNull(arg: T) = when(arg) {
    is Int -> false
    null -> true
}

fun testNullableInt(arg: Int?) = when (arg) {
    null -> true
    is Int -> false
}

fun testNullable(arg: Any?) = when (arg) {
    null -> true
    is Any -> false
}

fun testNullable(arg: Nothing?) = when (arg) {
    <!SENSELESS_COMPARISON!>null<!> -> true
}

fun testNullable(arg: Unit?) = when (arg) {
    null -> true
    is Unit -> false
}

fun testNullable(arg: IntArray?) = when (arg) {
    null -> true
    is IntArray -> false
}

fun testNullable(arg: UInt?) = when (arg) {
    null -> true
    is UInt -> false
}

typealias NullableInt = Int?

fun testTypeAliasToNullable(arg: NullableInt) = when (arg) {
    null -> true
    is NullableInt -> false
}

fun NullableInt.isNotNull() = when (this) {
    null -> false
    is NullableInt -> true
}

class KClassWithGetter {
    var prop: Int? = 0
        get() = when (prop) {
            null -> null
            is Int -> prop
        }
}

fun testSubclass(arg: String?) = when (arg) {
    null -> true
    is CharSequence -> false
}

fun testSmartCast(x: Any?) {
    if (x !is String?) return

    return when (x) {
        is CharSequence -> Unit
        null -> Unit
    }
}

val testLambda = {arg: String? -> when (arg) {
    null -> true
    is CharSequence -> false
}}

fun testLambda(arg: (() -> Unit)?) = when (arg) {
    null -> true
    is ()->Unit -> false
}

sealed class SealedClass {
    class A(val a: String) : SealedClass()
    class B(val b: String) : SealedClass()
}

fun testSealedClass(arg: SealedClass?) {
    when (arg) {
        is SealedClass.A? -> println(arg?.a)
        is SealedClass.B -> println(arg.b)
    }
}

fun testJavaNullableProps() {
    var a = when (JClass.intProp) {
        <!SENSELESS_NULL_IN_WHEN!>null<!> -> true
        is Int -> false
    }

    a = when (JClass.intProp) {
        is Int -> false
    }

    a = when (JClass.integerProp) {
        null -> true
        is Int -> false
    }

    a = when (JClass.stringProp) {
        null -> true
        is String -> false
    }

    a = when (JClass.objectProp) {
        null -> true
        is Any -> false
    }
}

fun testWhenStatementWithComma(arg: Int?): Int {
    return when(arg) {
        is Int, null -> 2
    }
}

sealed class Value

fun test(value: Value?) {
    val x = when (value) {
        is Value -> 1
        null -> 2
    }
}

class Inv<T>(val x: T)

fun testCaptured1(inv1: Inv<*>, inv2: Inv<out Number?>) {
    val arg1 = when (inv1.x) {
        is Any -> true
        <!SENSELESS_COMPARISON!>null<!> -> false
    }

    val arg2 = when (inv2.x) {
        is Number -> true
        null -> false
    }
}

fun typeErased(list: MutableList<String>?) = when (list) {
    is MutableList -> 1
    null -> 2
}

fun <T> testDNN(arg: T& Any) = when (arg) {
    is T -> false
}

fun isNullable(a: Int?) = when (a) {
    <!USELESS_IS_CHECK!>is Number?<!> -> false
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, disjunctionExpression, dnnType, equalityExpression,
funWithExtensionReceiver, functionDeclaration, functionalType, getter, ifExpression, integerLiteral, isExpression,
lambdaLiteral, localProperty, nestedClass, nullableType, outProjection, primaryConstructor, propertyDeclaration,
safeCall, sealed, smartcast, starProjection, typeAliasDeclaration, typeConstraint, typeParameter, whenExpression,
whenWithSubject */
