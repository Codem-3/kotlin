// RUN_PIPELINE_TILL: BACKEND
// DIAGNOSTICS: +UNUSED_PARAMETER
var y: Int = 1

// No backing field!
<!MUST_BE_INITIALIZED!>var x: Int<!>
    get() = y
    set(<!ACCESSOR_PARAMETER_NAME_SHADOWING, UNUSED_PARAMETER!>field<!>) {
        y = field
    }

/* GENERATED_FIR_TAGS: assignment, getter, integerLiteral, propertyDeclaration, setter */
