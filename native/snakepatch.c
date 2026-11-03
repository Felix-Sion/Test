#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <jni.h>
#include <stdint.h>
#include <string.h>

#if !defined(__x86_64__)
#error This controlled inline-patch demonstration requires Windows x64.
#endif

extern int snake_color_target(void);
extern int snake_color_replacement(void);

enum { LANDING_SIZE = 4, PATCH_SIZE = 16, ENTRY_SIZE = 20 };
static SRWLOCK code_lock = SRWLOCK_INIT;
static BOOL initialized = FALSE;
static unsigned char original[PATCH_SIZE];
static unsigned char patched[PATCH_SIZE];

static unsigned char *target_entry(void) {
    return (unsigned char *)(uintptr_t)&snake_color_target;
}

static unsigned char *patch_site(void) {
    return target_entry() + LANDING_SIZE;
}

JNIEXPORT jint JNICALL Java_snake_NativeColorPatch_nativeInitialize(
        JNIEnv *env, jclass type) {
    (void)env;
    (void)type;
    DWORD result = ERROR_SUCCESS;
    AcquireSRWLockExclusive(&code_lock);
    if (!initialized) {
        const unsigned char landing[] = {0xf3, 0x0f, 0x1e, 0xfa};
        if (memcmp(target_entry(), landing, LANDING_SIZE) != 0) {
            result = ERROR_INVALID_DATA;
        }
        for (int i = 0; i < PATCH_SIZE && result == ERROR_SUCCESS; ++i) {
            if (patch_site()[i] != 0x90) result = ERROR_INVALID_DATA;
        }
        if (result == ERROR_SUCCESS) {
            memcpy(original, patch_site(), PATCH_SIZE);
            memset(patched, 0x90, PATCH_SIZE);
            // FF 25 00 00 00 00 = JMP QWORD PTR [RIP+0]. The next 8 bytes
            // contain the real ASLR-adjusted destination, followed by 2 NOPs.
            patched[0] = 0xff;
            patched[1] = 0x25;
            memset(patched + 2, 0, 4);
            uintptr_t destination = (uintptr_t)&snake_color_replacement;
            memcpy(patched + 6, &destination, sizeof(destination));
            initialized = TRUE;
        }
    }
    ReleaseSRWLockExclusive(&code_lock);
    return (jint)result;
}

JNIEXPORT jint JNICALL Java_snake_NativeColorPatch_nativeColor(
        JNIEnv *env, jclass type) {
    (void)env;
    (void)type;
    // Every call into the patchable function holds this shared lock until
    // after the leaf function returns. Writers therefore have a safe point.
    AcquireSRWLockShared(&code_lock);
    int color = snake_color_target();
    ReleaseSRWLockShared(&code_lock);
    return (jint)color;
}

JNIEXPORT jint JNICALL Java_snake_NativeColorPatch_nativeSetApplied(
        JNIEnv *env, jclass type, jboolean apply) {
    (void)env;
    (void)type;
    DWORD result = ERROR_SUCCESS;
    AcquireSRWLockExclusive(&code_lock);
    if (!initialized) {
        result = ERROR_INVALID_STATE;
    } else {
        unsigned char *site = patch_site();
        const unsigned char *wanted = apply ? patched : original;
        if (memcmp(site, original, PATCH_SIZE) != 0
                && memcmp(site, patched, PATCH_SIZE) != 0) {
            // Refuse to overwrite an entry changed by something else.
            result = ERROR_INVALID_DATA;
        } else if (memcmp(site, wanted, PATCH_SIZE) != 0) {
            DWORD previous = 0;
            if (!VirtualProtect(site, PATCH_SIZE, PAGE_EXECUTE_READWRITE, &previous)) {
                result = GetLastError();
            } else {
                memcpy(site, wanted, PATCH_SIZE);
                if (!FlushInstructionCache(GetCurrentProcess(), site, PATCH_SIZE)) {
                    result = GetLastError();
                }
                DWORD ignored = 0;
                if (!VirtualProtect(site, PATCH_SIZE, previous, &ignored)) {
                    result = GetLastError();
                }
            }
        }
    }
    ReleaseSRWLockExclusive(&code_lock);
    return (jint)result;
}

JNIEXPORT jlongArray JNICALL Java_snake_NativeColorPatch_nativeSnapshot(
        JNIEnv *env, jclass type) {
    (void)type;
    jlong values[8] = {0};
    AcquireSRWLockShared(&code_lock);
    values[0] = (jlong)GetCurrentProcessId();
    values[1] = (jlong)(uintptr_t)&snake_color_target;
    values[2] = (jlong)(uintptr_t)&snake_color_replacement;
    values[3] = initialized && memcmp(patch_site(), patched, PATCH_SIZE) == 0;
    MEMORY_BASIC_INFORMATION info;
    if (VirtualQuery(patch_site(), &info, sizeof(info))) {
        values[4] = (jlong)info.Protect;
    }
    for (int i = 0; i < ENTRY_SIZE; ++i) {
        values[5 + i / 8] |= (jlong)((uint64_t)target_entry()[i] << ((i % 8) * 8));
    }
    ReleaseSRWLockShared(&code_lock);
    jlongArray array = (*env)->NewLongArray(env, 8);
    if (array != NULL) (*env)->SetLongArrayRegion(env, array, 0, 8, values);
    return array;
}
