LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := Nominatem
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := Nominatem

nominatem_root  := $(LOCAL_PATH)
nominatem_out   := $(OUT_DIR)/target/common/obj/APPS/$(LOCAL_MODULE)_intermediates
nominatem_build := $(nominatem_root)/build
nominatem_apk   := build/outputs/apk/Nominatem-release-unsigned.apk

$(nominatem_root)/$(nominatem_apk):
	rm -Rf $(nominatem_build)
	mkdir -p $(nominatem_out)
	mkdir -p $(nominatem_build)
	ln -sf $(nominatem_out) $(nominatem_build)
	cd $(nominatem_root) && JAVA_TOOL_OPTIONS="$(JAVA_TOOL_OPTIONS) -Dfile.encoding=UTF8" ./gradlew assembleRelease

LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(nominatem_apk)
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)
