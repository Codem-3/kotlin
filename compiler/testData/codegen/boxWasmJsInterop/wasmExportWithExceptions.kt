// TARGET_BACKEND: WASM
/// MODULE: main
// FILE: main.kt

import kotlin.wasm.WasmExport

@WasmExport
fun runWithException() {
    throw AssertionError("Some random exception").apply {
        println(this.stackTraceToString())
    }
}

fun box() = "OK"

// FILE: entry.mjs
import { runWithException } from "./index.mjs"

let nothrow = false;
try {
    runWithException()
    nothrow = true
} catch(e) {
    if (!(e instanceof Error)) {
        throw Error("Expected instance of Error, but '" + e.name +"' ('" + e.constructor.name + "') was received")
    }

    if (e.name !== "AssertionError" ) {
        throw Error("Wrong e.name = '" + e.name + "'")
    }
}

if (nothrow) throw Error("Unexpected successful call");
