// RUN_PIPELINE_TILL: FRONTEND
fun test(<!REDECLARATION!>a<!>: Int, <!REDECLARATION!>a<!>: String) {}

fun test2(block: (Int, String) -> Unit) { }

fun main() {
    test2 { <!REDECLARATION!>b<!>, <!REDECLARATION!>b<!> -> ; }

    val func: (Int, Int) -> Int = fun(_, _): Int { return 42 }
}

/* GENERATED_FIR_TAGS: anonymousFunction, functionDeclaration, functionalType, integerLiteral, lambdaLiteral,
localProperty, propertyDeclaration */
