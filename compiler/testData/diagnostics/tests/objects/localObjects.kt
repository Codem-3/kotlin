// RUN_PIPELINE_TILL: FRONTEND
// DIAGNOSTICS: -UNUSED_VARIABLE

fun foo() {
    <!LOCAL_OBJECT_NOT_ALLOWED!>object a<!> {}
    val b = object {
        <!LOCAL_OBJECT_NOT_ALLOWED!>object c<!> {}
    }
    b.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>c<!>
    class A {
        <!LOCAL_OBJECT_NOT_ALLOWED!>object d<!> {}
    }
    val f = {
        <!LOCAL_OBJECT_NOT_ALLOWED!>object e<!> {}
    }
}

/* GENERATED_FIR_TAGS: anonymousObjectExpression, classDeclaration, functionDeclaration, lambdaLiteral, localClass,
localProperty, nestedClass, objectDeclaration, propertyDeclaration */
