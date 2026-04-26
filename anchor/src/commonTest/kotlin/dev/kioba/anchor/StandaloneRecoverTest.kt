package dev.kioba.anchor

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class StandaloneRecoverTest {
  // -- Result variant --

  @Test
  fun `recover returns Ok on success`() {
    val result: Recover<TestError, String> = recover { "hello" }

    assertIs<Recover.Ok<String>>(result)
    assertEquals("hello", result.value)
  }

  @Test
  fun `recover returns Error on raise`() {
    val result: Recover<TestError, String> = recover { raise(TestError.NotFound) }

    assertIs<Recover.Error<TestError>>(result)
    assertEquals(TestError.NotFound, result.error)
  }

  @Test
  fun `recover preserves typed error details`() {
    val result: Recover<TestError, String> =
      recover {
        raise(TestError.Invalid("bad"))
      }

    assertIs<Recover.Error<TestError>>(result)
    val error = result.error
    assertIs<TestError.Invalid>(error)
    assertEquals("bad", error.reason)
  }

  @Test
  fun `recover does not catch unrelated exceptions`() {
    assertFailsWith<IllegalStateException> {
      recover<TestError, String> { throw IllegalStateException("boom") }
    }
  }

  // -- Extensions --

  @Test
  fun `getOrNull returns value on Ok`() {
    val result: Recover<TestError, Int> = recover { 42 }
    assertEquals(42, result.getOrNull())
  }

  @Test
  fun `getOrNull returns null on Error`() {
    val result: Recover<TestError, Int> = recover { raise(TestError.NotFound) }
    assertNull(result.getOrNull())
  }

  @Test
  fun `getErrorOrNull returns null on Ok`() {
    val result: Recover<TestError, Int> = recover { 42 }
    assertNull(result.getErrorOrNull())
  }

  @Test
  fun `getErrorOrNull returns error on Error`() {
    val result: Recover<TestError, Int> = recover { raise(TestError.NotFound) }
    assertEquals(TestError.NotFound, result.getErrorOrNull())
  }

  @Test
  fun `getOrElse returns value on Ok`() {
    val result: Recover<TestError, Int> = recover { 42 }
    assertEquals(42, result.getOrElse { -1 })
  }

  @Test
  fun `getOrElse returns fallback on Error`() {
    val result: Recover<TestError, Int> = recover { raise(TestError.NotFound) }
    assertEquals(-1, result.getOrElse { -1 })
  }

  @Test
  fun `fold maps Ok`() {
    val result: Recover<TestError, Int> = recover { 42 }
    val mapped = result.fold(onError = { "error" }, onOk = { "value=$it" })
    assertEquals("value=42", mapped)
  }

  @Test
  fun `fold maps Error`() {
    val result: Recover<TestError, Int> = recover { raise(TestError.NotFound) }
    val mapped = result.fold(onError = { "error=$it" }, onOk = { "value" })
    assertEquals("error=NotFound", mapped)
  }

  // -- Nesting --

  @Test
  fun `nested recover catches inner raise only`() {
    val outer: Recover<TestError, Recover<TestError, String>> =
      recover {
        val inner: Recover<TestError, String> = recover { raise(TestError.NotFound) }
        inner
      }

    assertIs<Recover.Ok<Recover<TestError, String>>>(outer)
    assertIs<Recover.Error<TestError>>(outer.value)
    assertEquals(TestError.NotFound, outer.value.error)
  }

  @Test
  fun `outer recover catches raise propagated from inner via getOrRaise`() {
    val outer: Recover<TestError, String> =
      recover {
        recover<TestError, String> { raise(TestError.NotFound) }.getOrRaise()
      }

    assertIs<Recover.Error<TestError>>(outer)
    assertEquals(TestError.NotFound, outer.error)
  }

  // -- ensure --

  @Test
  fun `ensure passes when condition is true`() {
    val result: Recover<TestError, String> =
      recover {
        ensure(true) { TestError.NotFound }
        "ok"
      }

    assertIs<Recover.Ok<String>>(result)
    assertEquals("ok", result.value)
  }

  @Test
  fun `ensure raises when condition is false`() {
    val result: Recover<TestError, String> =
      recover {
        ensure(false) { TestError.NotFound }
        "ok"
      }

    assertIs<Recover.Error<TestError>>(result)
    assertEquals(TestError.NotFound, result.error)
    Unit
  }

  // -- Raise extension function --

  @Test
  fun `Raise extension function raise is caught by standalone recover`() {
    fun Raise<TestError>.validate(
      value: Int,
    ): String {
      ensure(value > 0) { TestError.Invalid("must be positive") }
      return "valid: $value"
    }

    val result: Recover<TestError, String> = recover { validate(-1) }

    assertIs<Recover.Error<TestError>>(result)
    assertIs<TestError.Invalid>((result as Recover.Error).error)
    Unit
  }

  // -- Unhandled raise propagates --

  @Test
  fun `unhandled raise propagates as RaisedException`() {
    val result =
      recover<TestError, String> {
        recover<TestError, String> { raise(TestError.NotFound) }.getOrRaise()
      }

    assertIs<Recover.Error<TestError>>(result)
    assertEquals(TestError.NotFound, result.error)
  }

  // -- getOrRaise --

  @Test
  fun `getOrRaise unwraps Ok`() {
    val result: Recover<TestError, String> =
      recover {
        val inner = recover<TestError, String> { "hello" }
        inner.getOrRaise()
      }

    assertIs<Recover.Ok<String>>(result)
    assertEquals("hello", result.value)
  }

  @Test
  fun `getOrRaise re-raises Error`() {
    val result: Recover<TestError, String> =
      recover {
        val inner = recover<TestError, String> { raise(TestError.NotFound) }
        inner.getOrRaise()
      }

    assertIs<Recover.Error<TestError>>(result)
    assertEquals(TestError.NotFound, result.error)
  }

  // -- Anchor-level recover --

  @Test
  fun `Anchor-level recover returns Ok on success`() =
    runBlocking {
      val anchor =
        dev.kioba.anchor.internal.AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
        )

      val result = anchor.recover { "success" }

      assertIs<Recover.Ok<String>>(result)
      assertEquals("success", result.value)
    }

  @Test
  fun `Anchor-level recover catches raise`() =
    runBlocking {
      val anchor =
        dev.kioba.anchor.internal.AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
        )

      val result = anchor.recover { raise(TestError.NotFound) }

      assertIs<Recover.Error<TestError>>(result)
      assertEquals(TestError.NotFound, result.error)
    }

  @Test
  fun `Anchor-level recover with getOrRaise re-raises`() =
    runBlocking {
      val anchor =
        dev.kioba.anchor.internal.AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
        )

      val exception =
        assertFailsWith<RaisedException> {
          with(anchor) {
            recover { raise(TestError.NotFound) }.getOrRaise()
          }
        }
      assertEquals(TestError.NotFound, exception.error)
    }
}
