# -----------------------------------
# Configuration
# -----------------------------------

# Call sub-makefiles
include $(call all-subdir-makefiles)

# Local path
LOCAL_PATH := $(call my-dir)

# -----------------------------------
# Aubio library (prebuilt)
# -----------------------------------

include $(CLEAR_VARS)

LOCAL_MODULE := aubio
LOCAL_SRC_FILES := ../native/lib/libaubio.a

include $(PREBUILT_STATIC_LIBRARY)

# -----------------------------------
# Audio processing functions
# -----------------------------------

include $(CLEAR_VARS)

# Module name
LOCAL_MODULE := audioproc

# Add includes
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../native/include/

# Add source files
LOCAL_SRC_FILES := functions.c

# Add static libraries
LOCAL_STATIC_LIBRARIES := aubio

include $(BUILD_SHARED_LIBRARY)
# -----------------------------------
