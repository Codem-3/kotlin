// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// LANGUAGE: +SoundSmartCastsAfterTry

fun bar() {}

fun foo() {
    var s: String?
    s = "Test"
    try {
        s = null
    }
    catch (ex: Exception) {}
    finally {
        bar()
    }
    s<!UNSAFE_CALL!>.<!>hashCode()
}

/* GENERATED_FIR_TAGS: assignment, functionDeclaration, localProperty, nullableType, propertyDeclaration, stringLiteral,
tryExpression */
