// RUN_PIPELINE_TILL: FRONTEND
// FILE: File1.kt
package pack1

public class SomeClass {
    private class N
    public open class PublicNested
}

// FILE: Main.kt
package a

import pack1.SomeClass.*

private class X : <!EXPOSED_SUPER_CLASS!><!FINAL_SUPERTYPE, INVISIBLE_MEMBER, INVISIBLE_REFERENCE!>N<!>()<!>

/* GENERATED_FIR_TAGS: classDeclaration, nestedClass */
