NominatimGeocoderBackend
========================
[UnifiedNlp](https://github.com/microg/android_packages_apps_UnifiedNlp) geocoder backend that uses MapQuest's Nominatim service (based on OpenStreeMap)

Building
--------
Build using gradle. Remember to `git submodule init` before.

Automatied Builds
------------------
**Travis:** [![Build Status](https://travis-ci.org/microg/NominatimGeocoderBackend.png?branch=master)](https://travis-ci.org/microg/NominatimGeocoderBackend)
**CircleCI:** [![CircleCI](https://circleci.com/gh/microg/NominatimGeocoderBackend/tree/master.png?style=badge)](https://circleci.com/gh/microg/NominatimGeocoderBackend)

The APK can be downloaded from CircleCI, [here](https://circleci.com/api/v1/project/microg/NominatimGeocoderBackend/latest/artifacts/0/$CIRCLE_ARTIFACTS/NominatimGeocoderBackend.apk?filter=successful&branch=master)

or via QR-Code
[![here](https://circleci.com/api/v1/project/zoff99/NominatimGeocoderBackend/latest/artifacts/0/$CIRCLE_ARTIFACTS/QR_apk.png?filter=successful&branch=master)](https://circleci.com/api/v1/project/microg/NominatimGeocoderBackend/latest/artifacts/0/$CIRCLE_ARTIFACTS/NominatimGeocoderBackend.apk?filter=successful&branch=master)

Used libraries
--------------
-	[UnifiedNlpApi](https://github.com/microg/android_external_UnifiedNlpApi)

License
-------
    Copyright 2014-2015 μg Project Team
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

The launcher icon (res/drawable-*/ic_launcher.png) is created using the [Launcher Icon Generator from Android Asset Studio](https://android-ui-utils.googlecode.com/hg/asset-studio/dist/icons-launcher.html) and thus licensed under [CC BY 3.0](http://creativecommons.org/licenses/by/3.0/).
