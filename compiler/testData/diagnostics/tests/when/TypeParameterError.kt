// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +ForbidInferringTypeVariablesIntoEmptyIntersection
// FILE: ObjectNode.java

public interface ObjectNode {
    <T extends JsonNode> T set(String fieldName, JsonNode value);
}

// FILE: JsonNode.java

public class JsonNode

// FILE: test.kt

interface JsonObject
class SomeJsonObject() : JsonObject

fun String.put(value: JsonObject?, node: ObjectNode) {
    <!INFERRED_TYPE_VARIABLE_INTO_EMPTY_INTERSECTION_ERROR!>select<!>(
        node.set(this, null),
        Unit,
    )

    <!INFERRED_TYPE_VARIABLE_INTO_EMPTY_INTERSECTION_ERROR!>if (value == null) node.set(this, null)
    else if (value is SomeJsonObject) Unit<!>

    <!INFERRED_TYPE_VARIABLE_INTO_EMPTY_INTERSECTION_ERROR!>when (value) {
        null -> node.set(this, null)
        is SomeJsonObject -> Unit
        else -> TODO()
    }<!>
}

fun TODO(): Nothing = null!!
fun <K> select(vararg values: K): K = values[0]

/* GENERATED_FIR_TAGS: capturedType, checkNotNullCall, classDeclaration, equalityExpression, flexibleType,
funWithExtensionReceiver, functionDeclaration, ifExpression, integerLiteral, interfaceDeclaration, isExpression,
javaFunction, javaType, nullableType, outProjection, primaryConstructor, smartcast, thisExpression, typeParameter,
vararg, whenExpression, whenWithSubject */
