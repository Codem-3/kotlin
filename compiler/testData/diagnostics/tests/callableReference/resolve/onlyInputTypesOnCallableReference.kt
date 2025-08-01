// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
// SKIP_TXT
// WITH_STDLIB

open class BaseClass
class DerivedClass : BaseClass()

fun test() {
    val derivedToStringMap: Map<DerivedClass, String> = mapOf()
    val mapper: (BaseClass) -> String? = derivedToStringMap::get

    foo(mapper)
    foo(derivedToStringMap::get)
}


fun foo(mapper: (BaseClass) -> String?) {}

/* GENERATED_FIR_TAGS: callableReference, classDeclaration, functionDeclaration, functionalType, localProperty,
nullableType, propertyDeclaration */
