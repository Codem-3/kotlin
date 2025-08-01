// RUN_PIPELINE_TILL: FRONTEND
interface Some {
    <!REDUNDANT_OPEN_IN_INTERFACE!>open<!> fun foo()
    open fun bar() {}

    <!REDUNDANT_OPEN_IN_INTERFACE!>open<!> val x: Int
    open val y = <!PROPERTY_INITIALIZER_IN_INTERFACE!>1<!>
    open val z get() = 1

    <!REDUNDANT_OPEN_IN_INTERFACE!>open<!> var xx: Int
    open var yy = <!PROPERTY_INITIALIZER_IN_INTERFACE!>1<!>
    open <!BACKING_FIELD_IN_INTERFACE!>var zz: Int<!>
        set(value) {
            field = value
        }
}

/* GENERATED_FIR_TAGS: assignment, functionDeclaration, getter, integerLiteral, interfaceDeclaration,
propertyDeclaration, setter */
