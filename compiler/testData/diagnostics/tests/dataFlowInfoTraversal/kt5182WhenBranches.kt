// RUN_PIPELINE_TILL: BACKEND
//KT-5182 Data flow info is lost for 'when' branches

open class A

class B: A() {
    fun foo() = 1
}

fun foo(a: A) = when {
    a !is B -> 2
    true -> <!DEBUG_INFO_SMARTCAST!>a<!>.foo() //'foo' is unresolved, smart cast doesn't work
    else -> <!DEBUG_INFO_SMARTCAST!>a<!>.foo()
}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, integerLiteral, isExpression, smartcast, whenExpression */
