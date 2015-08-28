LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := OpenCVLibrary-2.4.11
LOCAL_SRC_FILES := OpenCVLibrary-2.4.11.cpp

include $(BUILD_SHARED_LIBRARY)
