// RUN_PIPELINE_TILL: BACKEND
// KT-524 sure() returns T

package StringBuilder

//import kotlin.io.*
//import java.io.*

fun main() {
}

val Int.bd : StringBuilder get() = StringBuilder(this.toString())
fun StringBuilder.plus(other : StringBuilder) : StringBuilder = this.append(other).sure1() // !!!

fun <T : Any> T?.sure1() : T { return if (this != null) this else throw NullPointerException() }

/* GENERATED_FIR_TAGS: dnnType, equalityExpression, flexibleType, funWithExtensionReceiver, functionDeclaration, getter,
ifExpression, javaFunction, nullableType, propertyDeclaration, propertyWithExtensionReceiver, smartcast, thisExpression,
typeConstraint, typeParameter */
