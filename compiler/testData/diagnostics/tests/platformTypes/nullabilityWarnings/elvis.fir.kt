// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_VARIABLE -SENSELESS_COMPARISON, -UNUSED_PARAMETER

// FILE: J.java

import org.jetbrains.annotations.*;

public class J {
    @NotNull
    public static J staticNN;
    @Nullable
    public static J staticN;
    public static J staticJ;

    public static <T> T getAny() {
        return null;
    }

    @NotNull
    public static <T> T getNNAny() {
        return (T) null;
    }

    @Nullable
    public static <T> T getNAny() {
        return null;
    }
}

// FILE: k.kt

fun test() {
    // @NotNull platform type
    val platformNN = J.staticNN
    // @Nullable platform type
    val platformN = J.staticN
    // platform type with no annotation
    val platformJ = J.staticJ

    val v0 = platformNN <!USELESS_ELVIS!>?: J()<!>
    platformNN <!USELESS_ELVIS!>?: J()<!>
    platformN ?: J()
    platformJ ?: J()

    if (platformNN != null) {
        platformNN <!USELESS_ELVIS!>?: J()<!>
    }

    if (platformN != null) {
        platformN <!USELESS_ELVIS!>?: J()<!>
    }

    if (platformJ != null) {
        platformJ <!USELESS_ELVIS!>?: J()<!>
    }

    takeNotNull(J.staticNN <!USELESS_ELVIS!>?: J()<!>)
    takeNotNull(J.staticN ?: J())
    takeNotNull(J.staticJ ?: J())
    takeNotNull(J.getAny() ?: J())
    takeNotNull(J.getNNAny() <!USELESS_ELVIS!>?: J()<!>)
    takeNotNull(J.getNAny() ?: J())

    val x = <!UNRESOLVED_REFERENCE!>unresolved<!> ?: null
    val y = <!UNRESOLVED_REFERENCE!>unresolved<!>.foo ?: return
}

fun takeNotNull(s: J) {}

/* GENERATED_FIR_TAGS: elvisExpression, equalityExpression, flexibleType, functionDeclaration, ifExpression,
javaFunction, javaProperty, javaType, localProperty, nullableType, propertyDeclaration, smartcast */
