// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-47815

interface A : <!INTERFACE_WITH_SUPERCLASS!>Test<!>

open class Test {
    fun <T> result() = object : A { }
}

/* GENERATED_FIR_TAGS: anonymousObjectExpression, classDeclaration, functionDeclaration, interfaceDeclaration,
nullableType, typeParameter */
