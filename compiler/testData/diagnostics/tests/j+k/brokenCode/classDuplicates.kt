// RUN_PIPELINE_TILL: FRONTEND
// FILE: Hello.kt
private class Hello()
{
    val a = 4
}

fun test() {
    // no exception is thrown (see KT-3897)
    Hello().a
}

// FILE: Hello.java
public class Hello {}

/* GENERATED_FIR_TAGS: classDeclaration, functionDeclaration, integerLiteral, primaryConstructor, propertyDeclaration */
