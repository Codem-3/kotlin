// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
private enum class Foo { A, B }

private class Bar(val foo: Foo)

/* GENERATED_FIR_TAGS: classDeclaration, enumDeclaration, enumEntry, primaryConstructor, propertyDeclaration */
