// IGNORE_FIR_DIAGNOSTICS
// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +MultiPlatformProjects
// ISSUE: KT-66960

// MODULE: common
// FILE: common.kt
expect sealed class Base()

class CommonDerived : Base()

// should be an error
fun commonTest(x: Base) = <!EXPECT_TYPE_IN_WHEN_WITHOUT_ELSE, NO_ELSE_IN_WHEN, NO_ELSE_IN_WHEN{JVM}!>when<!> (x) {
    is CommonDerived -> 1
}

// MODULE: main()()(common)
// FILE: test.kt
actual typealias Base = PlatformBase

sealed class PlatformBase

class PlatformDerived : PlatformBase()

// should be ok
fun platformTest_1(x: Base) = when (x) {
    is CommonDerived -> 1
    is PlatformDerived -> 2
}

// should be an error
fun platformTest_2(x: Base) = <!NO_ELSE_IN_WHEN!>when<!> (x) {
    is PlatformDerived -> 2
}

/* GENERATED_FIR_TAGS: actual, classDeclaration, expect, functionDeclaration, integerLiteral, isExpression,
primaryConstructor, sealed, smartcast, typeAliasDeclaration, whenExpression, whenWithSubject */
