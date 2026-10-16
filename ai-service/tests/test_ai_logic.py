"""Pure-logic tests: run with `python -m pytest` (or `python -m unittest`); no network, no FastAPI needed."""
import asyncio
import sys
import types
import unittest
from types import SimpleNamespace

try:
    import httpx
except ImportError:
    stub = types.ModuleType("httpx")
    for name in ("AsyncClient", "Timeout", "AsyncBaseTransport", "TimeoutException", "TransportError"):
        setattr(stub, name, type(name, (Exception,), {}))
    sys.modules["httpx"] = stub

from app.config.settings import ConfigError, Settings
from app.services.context_service import AIError, ContextBuilder
from app.services.llm_service import MockProvider, error_from_status


def H(role, content):
    return SimpleNamespace(role=role, content=content)


def builder(**kw):
    return ContextBuilder(Settings(**{"max_context_messages": 4, "max_message_length": 100, "max_context_chars": 400, **kw}))


class ContextTests(unittest.TestCase):
    def test_keeps_order_roles_and_new_message_last(self):
        out = builder().build("c", [H("user", "a"), H("assistant", "b")])
        self.assertEqual([m["role"] for m in out], ["user", "assistant", "user"])
        self.assertEqual(out[-1]["content"], "c")

    def test_limits_history_to_newest_messages(self):
        hist = [H("user" if i % 2 == 0 else "assistant", f"m{i}") for i in range(10)]
        out = builder().build("new", hist)
        self.assertEqual([m["content"] for m in out], ["m6", "m7", "m8", "m9", "new"])

    def test_starts_with_user_and_merges_consecutive_roles(self):
        out = builder().build("again", [H("assistant", "orphan"), H("user", "q1"), H("user", "q2")])
        self.assertEqual(out[0]["role"], "user")
        self.assertEqual(len(out), 1)
        self.assertIn("q1", out[0]["content"])
        self.assertIn("again", out[0]["content"])

    def test_character_budget(self):
        out = builder(max_context_chars=120).build("x" * 50, [H("user", "a" * 100), H("assistant", "b" * 60)])
        self.assertEqual([m["content"] for m in out], ["x" * 50])

    def test_rejects_empty_and_too_long(self):
        with self.assertRaises(AIError) as e:
            builder().build("   ", [])
        self.assertEqual(e.exception.code, "INVALID_REQUEST")
        with self.assertRaises(AIError) as e:
            builder().build("x" * 101, [])
        self.assertEqual(e.exception.code, "MESSAGE_TOO_LONG")

    def test_blank_history_is_dropped(self):
        out = builder().build("q", [H("user", "  "), H("assistant", "")])
        self.assertEqual(len(out), 1)


class ErrorMappingTests(unittest.TestCase):
    def test_codes(self):
        self.assertEqual(error_from_status(401, "", None).code, "LLM_AUTH")
        e = error_from_status(429, "", "12")
        self.assertEqual((e.code, e.retry_after), ("LLM_RATE_LIMITED", 12))
        self.assertEqual(error_from_status(400, "context_length_exceeded", None).code, "CONTEXT_TOO_LARGE")
        self.assertEqual(error_from_status(503, "", None).code, "LLM_UNAVAILABLE")
        self.assertEqual(error_from_status(400, "bad param", None).code, "LLM_ERROR")

    def test_messages_never_contain_provider_body(self):
        e = error_from_status(500, "SECRET sk-123 http://internal", None)
        self.assertNotIn("sk-123", e.message)


class MockProviderTests(unittest.TestCase):
    def test_stream_and_failure_switch(self):
        p = MockProvider(Settings())
        msgs = [{"role": "user", "content": "hello"}]

        async def collect():
            return "".join([c async for c in p.stream("sys", msgs)])

        self.assertIn("hello", asyncio.run(collect()))
        with self.assertRaises(AIError) as e:
            asyncio.run(p.complete("sys", [{"role": "user", "content": "[[fail:timeout]]"}]))
        self.assertEqual(e.exception.code, "LLM_TIMEOUT")


class SettingsTests(unittest.TestCase):
    def test_validation(self):
        with self.assertRaises(ConfigError):
            Settings().validate()
        Settings(api_key="k").validate()
        with self.assertRaises(ConfigError):
            Settings(api_key="k", provider="openai", model="m").validate()
        with self.assertRaises(ConfigError):
            Settings(api_key="k", provider="nope").validate()
        self.assertNotIn("secret", repr(Settings(llm_api_key="secret")))


if __name__ == "__main__":
    unittest.main()
