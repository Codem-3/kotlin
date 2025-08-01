
// FILE: test.kt

fun box() {
    test(B(A()))
}

class A

class B(val a: A) {
    operator fun A.invoke() {}
}

fun test(b: B) {
    with(b) {
        a()
    }
}

// EXPECTATIONS JVM_IR
// test.kt:5 box
// test.kt:8 <init>
// test.kt:5 box
// test.kt:10 <init>
// test.kt:5 box
// test.kt:15 test
// test.kt:16 test
// test.kt:10 getA
// test.kt:16 test
// test.kt:11 invoke
// test.kt:17 test
// test.kt:15 test
// test.kt:18 test
// test.kt:6 box

// EXPECTATIONS JS_IR
// test.kt:5 box
// test.kt:8 <init>
// test.kt:5 box
// test.kt:10 <init>
// test.kt:10 <init>
// test.kt:5 box
// test.kt:16 test
// test.kt:11 invoke
// test.kt:18 test
// test.kt:6 box

// EXPECTATIONS WASM
// test.kt:5 $box (9, 11)
// test.kt:8 $A.<init> (7)
// test.kt:5 $box (9)
// test.kt:10 $B.<init> (8, 17)
// test.kt:5 $box (4)
// test.kt:15 $test (9, 4)
// test.kt:16 $test (8)
// test.kt:11 $B.invoke (30)
// test.kt:16 $test (8)
// test.kt:17 $test (5)
// test.kt:18 $test (1)
// test.kt:6 $box (1)
