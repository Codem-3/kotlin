// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-74474

fun foo(x: () -> String) {}
fun main(a: Array<String>) {
    foo {
        <!RETURN_TYPE_MISMATCH!>a[0] = ""<!>
    }
}

/* GENERATED_FIR_TAGS: assignment, functionDeclaration, functionalType, integerLiteral, lambdaLiteral, stringLiteral */
