// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
// LANGUAGE:-ProhibitMissedMustBeInitializedWhenThereIsNoPrimaryConstructor
// DIAGNOSTICS: -DEBUG_INFO_LEAKING_THIS
class Foo() {
    constructor(x: Int) : this()

    <!MUST_BE_INITIALIZED!>var x: String<!>
        set(value) {}

    init {
        x = ""
    }
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, init, primaryConstructor, propertyDeclaration, secondaryConstructor,
setter, stringLiteral */
