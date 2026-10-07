#include "bbcode-syntax.h"

#include <jni.h>
#include <limits>
#include <new>
#include <stdexcept>
#include <string>

namespace {
void throwJava(JNIEnv* env, const char* type, const char* message) {
    jclass exception = env->FindClass(type);
    if (exception != nullptr) {
        env->ThrowNew(exception, message);
        env->DeleteLocalRef(exception);
    }
}
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_fimtale_editor_BbCodeSyntax_parsePacked(JNIEnv* env, jclass, jstring source) {
    if (source == nullptr) return env->NewIntArray(0);
    try {
        const jsize length = env->GetStringLength(source);
        // Copy UTF-16 code units without Modified UTF-8 conversion. Release the
        // JVM buffer before parsing and also if the C++ allocation fails.
        const jchar* chars = env->GetStringChars(source, nullptr);
        if (chars == nullptr) return nullptr;
        std::u16string text;
        try { text.assign(chars, chars + length); }
        catch (...) { env->ReleaseStringChars(source, chars); throw; }
        env->ReleaseStringChars(source, chars);
        auto packed = fimtale::bbcode::parseSyntax(text);
        if (packed.size() > static_cast<std::size_t>(std::numeric_limits<jsize>::max()))
            throw std::length_error("BBCode result is too large");
        auto result = env->NewIntArray(static_cast<jsize>(packed.size()));
        if (result != nullptr && !packed.empty())
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(packed.size()), packed.data());
        return result;
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "Cannot allocate BBCode parser buffers");
    } catch (const std::length_error&) {
        throwJava(env, "java/lang/OutOfMemoryError", "BBCode input or output is too large");
    } catch (const std::exception&) {
        throwJava(env, "java/lang/IllegalStateException", "Native BBCode parsing failed");
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "Unexpected native BBCode parser failure");
    }
    return nullptr;
}
