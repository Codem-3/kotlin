// RUN_PIPELINE_TILL: BACKEND
fun f() {
    foo {
        return@foo 1
    }
    foo({
        return@foo 1
    }
    )
    foo(a = {
        return@foo 1
    })

    foo {
        foo {
            return<!LABEL_NAME_CLASH!>@foo<!> 1
        }
        return@foo 1
    }
}

fun foo(a: Any) {}

/* GENERATED_FIR_TAGS: functionDeclaration, integerLiteral, lambdaLiteral */
