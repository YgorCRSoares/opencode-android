# Android.mk for the vendored jackpal libtermexec PTY sources.
#
# Written for this repository: upstream shipped no Android.mk (its AGP 1.0
# build generated one from the `ndk { moduleName ... }` block, removed in
# modern AGP). See vendor/libtermexec/VENDORED.md.
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# TermExec.java does System.loadLibrary("jackpal-termexec2") ->
# libjackpal-termexec2.so
LOCAL_MODULE := jackpal-termexec2
LOCAL_SRC_FILES := process.cpp
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
