// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL

class SimpleKlass {
    @Deprecated("deprecated and hidden", level = DeprecationLevel.HIDDEN)
    operator fun component1(): Int = 42
}

fun test(simpleKlass: SimpleKlass) {
    val (s1) = <!COMPONENT_FUNCTION_MISSING!>simpleKlass<!>
}

/* GENERATED_FIR_TAGS: classDeclaration, destructuringDeclaration, functionDeclaration, integerLiteral, localProperty,
operator, propertyDeclaration, stringLiteral */
