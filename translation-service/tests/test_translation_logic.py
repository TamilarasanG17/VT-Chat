"""Pure-Python tests (no model, no FastAPI): script detection, Thanglish detection/normalization and routing."""
import unittest

from app.services import language_service as langs
from app.services import tanglish_service as tg
from app.services.language_service import Script
from app.services.translation_service import TranslationError, TranslationService


class FakeEngine:
    def __init__(self):
        self.calls = []

    def is_ready(self):
        return True

    def translate(self, text, source, target):
        self.calls.append((text, source, target))
        return f"[{source}>{target}] {text}"


class ScriptTests(unittest.TestCase):
    def test_scripts(self):
        self.assertEqual(langs.detect_script("எப்படி இருக்க?"), Script.TAMIL)
        self.assertEqual(langs.detect_script("आप कैसे हैं"), Script.DEVANAGARI)
        self.assertEqual(langs.detect_script("സുഖമാണോ"), Script.MALAYALAM)
        self.assertEqual(langs.detect_script("ಹೇಗಿದ್ದೀರಾ"), Script.KANNADA)
        self.assertEqual(langs.detect_script("How are you"), Script.LATIN)
        self.assertEqual(langs.detect_script("12345 😀"), Script.NONE)
        self.assertEqual(langs.detect_script("お元気ですか"), Script.OTHER)
        self.assertEqual(langs.detect_script("ok எப்படி இருக்க"), Script.TAMIL)

    def test_directions(self):
        self.assertEqual(langs.direction_for("en", "ta"), langs.Direction.EN_INDIC)
        self.assertEqual(langs.direction_for("ta", "en"), langs.Direction.INDIC_EN)
        self.assertEqual(langs.direction_for("ta", "ml"), langs.Direction.INDIC_INDIC)


class ThanglishTests(unittest.TestCase):
    def test_detected(self):
        for text in ["epdi iruka", "saptiya", "enna panra", "enga pora", "romba nalla iruku", "naan office ku poren",
                     "meeting mudinjacha?", "lunch saptiya?", "today romba busy", "enna panra now", "romba good ah iruku"]:
            self.assertIsNotNone(tg.detect_romanized_tamil(text), text)

    def test_english_not_detected(self):
        for text in ["How are you?", "I need to go to the office today", "See you tomorrow, then we can talk about the plan",
                     "ok", "hello", "I told him that the ku was wrong in this long sentence"]:
            self.assertIsNone(tg.detect_romanized_tamil(text), text)

    def test_normalization(self):
        self.assertEqual(tg.transliterate_to_tamil("epdi iruka"), "எப்படி இருக்கா")
        self.assertEqual(tg.transliterate_to_tamil("naan office ku poren"), "நான் office க்கு போறேன்")
        self.assertEqual(tg.transliterate_to_tamil("lunch saptiya?"), "lunch சாப்டியா?")
        self.assertEqual(tg.transliterate_to_tamil("vandhen"), "வந்தேன்")


class RoutingTests(unittest.TestCase):
    def setUp(self):
        self.engine = FakeEngine()
        self.svc = TranslationService(self.engine)

    def test_en_to_ta(self):
        r = self.svc.translate("How are you?", "ta")
        self.assertEqual((r.sourceLanguage, r.sourceScript, r.romanized), ("en", "latin", False))
        self.assertEqual(self.engine.calls, [("How are you?", "en", "ta")])

    def test_ta_to_en_hi_ml_kn(self):
        for target in ("en", "hi", "ml", "kn"):
            r = self.svc.translate("வணக்கம்", target)
            self.assertEqual((r.sourceLanguage, r.sourceScript, r.romanized), ("ta", "tamil", False))
        self.assertEqual([(c[1], c[2]) for c in self.engine.calls], [("ta", "en"), ("ta", "hi"), ("ta", "ml"), ("ta", "kn")])

    def test_en_to_indic(self):
        for target in ("hi", "ml", "kn"):
            self.svc.translate("Good morning", target)
        self.assertEqual([c[2] for c in self.engine.calls], ["hi", "ml", "kn"])

    def test_hindi_to_tamil(self):
        r = self.svc.translate("आप कैसे हैं", "ta")
        self.assertEqual(r.sourceLanguage, "hi")
        self.assertEqual(self.engine.calls[0][1:], ("hi", "ta"))

    def test_thanglish_to_english_goes_through_tamil_script(self):
        r = self.svc.translate("epdi iruka", "en")
        self.assertEqual((r.sourceLanguage, r.sourceScript, r.romanized), ("ta", "romanized", True))
        self.assertEqual(self.engine.calls, [("எப்படி இருக்கா", "ta", "en")])

    def test_mixed_thanglish_keeps_english_words(self):
        self.svc.translate("naan office ku poren", "en")
        self.assertEqual(self.engine.calls[0][0], "நான் office க்கு போறேன்")

    def test_thanglish_to_tamil_needs_no_model(self):
        r = self.svc.translate("epdi iruka", "ta")
        self.assertEqual(r.translatedText, "எப்படி இருக்கா")
        self.assertEqual(self.engine.calls, [])

    def test_thanglish_to_hindi(self):
        self.svc.translate("romba nalla iruku", "hi")
        self.assertEqual(self.engine.calls[0][1:], ("ta", "hi"))

    def test_same_language_unchanged(self):
        r = self.svc.translate("வணக்கம்", "ta")
        self.assertEqual(r.translatedText, "வணக்கம்")
        self.assertEqual(self.engine.calls, [])

    def test_errors(self):
        with self.assertRaises(TranslationError) as e:
            self.svc.translate("hello", "fr")
        self.assertEqual(e.exception.code, "UNSUPPORTED_LANGUAGE")
        with self.assertRaises(TranslationError):
            self.svc.translate("   ", "ta")
        with self.assertRaises(TranslationError):
            self.svc.translate("お元気ですか", "en")

    def test_no_letters(self):
        r = self.svc.translate("12345 😀", "ta")
        self.assertEqual(r.translatedText, "12345 😀")
        self.assertIsNone(r.sourceLanguage)


if __name__ == "__main__":
    unittest.main()
