LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := NominatimNlpBackend
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := NominatimNlpBackend

nominatim_root  := $(LOCAL_PATH)
nominatim_out   := $(OUT_DIR)/target/common/obj/APPS/$(LOCAL_MODULE)_intermediates
nominatim_build := $(nominatim_root)/build
nominatim_apk   := build/outputs/apk/NominatimGeocoderBackend-release-unsigned.apk

$(nominatim_root)/$(nominatim_apk):
	rm -Rf $(nominatim_build)
	mkdir -p $(nominatim_out)
	mkdir -p $(nominatim_build)
	ln -sf $(nominatim_out) $(nominatim_build)
	cd $(nominatim_root) && JAVA_TOOL_OPTIONS="$(JAVA_TOOL_OPTIONS) -Dfile.encoding=UTF8" ./gradlew assembleRelease

LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(nominatim_apk)
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)
