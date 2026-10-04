#include <jni.h>
#include <string>
#include <algorithm>

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_fimtale_utils_GravitySensorHelper_calculateTransforms(
        JNIEnv* env,
        jobject,
        jfloat x,
        jfloat y) {

    const float MAX_ROTATION_ANGLE = 15.0f;
    const float MAX_TRANSLATION = 50.0f;

    float rotationY = x * 2.0f;
    float rotationX = -y * 2.0f;

    if (rotationY > MAX_ROTATION_ANGLE) rotationY = MAX_ROTATION_ANGLE;
    if (rotationY < -MAX_ROTATION_ANGLE) rotationY = -MAX_ROTATION_ANGLE;
    if (rotationX > MAX_ROTATION_ANGLE) rotationX = MAX_ROTATION_ANGLE;
    if (rotationX < -MAX_ROTATION_ANGLE) rotationX = -MAX_ROTATION_ANGLE;

    float translationX = x * 5.0f;
    float translationY = y * 5.0f;

    if (translationX > MAX_TRANSLATION) translationX = MAX_TRANSLATION;
    if (translationX < -MAX_TRANSLATION) translationX = -MAX_TRANSLATION;
    if (translationY > MAX_TRANSLATION) translationY = MAX_TRANSLATION;
    if (translationY < -MAX_TRANSLATION) translationY = -MAX_TRANSLATION;

    jfloatArray result = env->NewFloatArray(4);
    if (result == nullptr) {
        return nullptr;
    }

    jfloat temp[] = {rotationX, rotationY, translationX, translationY};
    env->SetFloatArrayRegion(result, 0, 4, temp);

    return result;
}
