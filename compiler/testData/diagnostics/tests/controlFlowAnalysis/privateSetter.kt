// RUN_PIPELINE_TILL: FRONTEND
// FIR_IDENTICAL
open class X(s : String) {
    public var n: String = s
        private set

}

class Z : X("subclass") {
    fun print(): String {
        <!INVISIBLE_SETTER!>n<!> = n
        return n;
    }
}


fun box() : String {
    return Z().print() //error
}

/* GENERATED_FIR_TAGS: assignment, classDeclaration, functionDeclaration, primaryConstructor, propertyDeclaration,
stringLiteral */
