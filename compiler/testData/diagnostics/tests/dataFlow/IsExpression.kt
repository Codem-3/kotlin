// RUN_PIPELINE_TILL: FRONTEND
fun f(a: Boolean, b: Int) {}

fun foo(a: Any) {
    f(a is Int, <!TYPE_MISMATCH!>a<!>)
    1 <!NONE_APPLICABLE!>+<!> a
}

/* GENERATED_FIR_TAGS: additiveExpression, functionDeclaration, isExpression */
