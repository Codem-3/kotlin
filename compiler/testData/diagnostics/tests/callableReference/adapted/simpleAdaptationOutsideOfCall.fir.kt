// RUN_PIPELINE_TILL: BACKEND
// SKIP_TXT
fun baz(options: String = ""): String = ""

fun runForString(x: () -> String) {}

fun foo(dumpStrategy: String) {
    val dump0: () -> String = ::baz

    runForString(::baz)
}

/* GENERATED_FIR_TAGS: callableReference, functionDeclaration, functionalType, localProperty, propertyDeclaration,
stringLiteral */
