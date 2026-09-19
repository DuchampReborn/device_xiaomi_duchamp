/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 * FUCK XIMI AND MTK
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <dlfcn.h>
#include <vector>
#include <optional>
#include <string>
#include <android/log.h>
#include <string.h>
#include <stdint.h>

#define LOG_TAG "audio_metadata_shim"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// A16 playback_track_metadata_v7 (272 bytes)
struct playback_track_metadata_a16 {
    int usage;
    int content_type;
    float gain;
};

struct playback_track_metadata_v7_a16 {
    struct playback_track_metadata_a16 base;
    uint32_t channel_mask;
    char tags[256];
};

// A17 playback_track_metadata_v7 (336 bytes)
struct playback_track_metadata_v7_a17 {
    struct playback_track_metadata_a16 base;
    uint32_t channel_mask;
    char tags[256];
    char codec_provenance[64];
};

extern "C" int _ZN7android8hardware5audio4V7_014implementation9CoreUtils21sourceMetadataToHalV7ERKNS1_6common4V7_014SourceMetadataEbPNSt3__16vectorI26playback_track_metadata_v7NSA_9allocatorISC_EEEE(
    const void* sourceMetadata, bool ignoreNonVendorTags, std::vector<playback_track_metadata_v7_a16>* out_vec) 
{
    // Load the real function from the system library
    static auto real_func = (int (*)(const void*, bool, std::vector<playback_track_metadata_v7_a17>*))
        dlsym(RTLD_NEXT, "_ZN7android8hardware5audio4V7_014implementation9CoreUtils21sourceMetadataToHalV7ERKNS1_6common4V7_014SourceMetadataEbPNSt3__16vectorI26playback_track_metadata_v7NSA_9allocatorISC_EEEE");

    if (!real_func) {
        void* handle = dlopen("android.hardware.audio.common@7.0-util.so", RTLD_NOW);
        if (handle) {
            real_func = (int (*)(const void*, bool, std::vector<playback_track_metadata_v7_a17>*))
                dlsym(handle, "_ZN7android8hardware5audio4V7_014implementation9CoreUtils21sourceMetadataToHalV7ERKNS1_6common4V7_014SourceMetadataEbPNSt3__16vectorI26playback_track_metadata_v7NSA_9allocatorISC_EEEE");
        }
    }

    if (!real_func) {
        ALOGE("Failed to find real sourceMetadataToHalV7!");
        return -1;
    }

    if (!out_vec) {
        // Caller just wants to validate the metadata without receiving the converted structs
        return real_func(sourceMetadata, ignoreNonVendorTags, nullptr);
    }

    // Call real function with a temporary A17 vector
    std::vector<playback_track_metadata_v7_a17> temp_a17;
    int status = real_func(sourceMetadata, ignoreNonVendorTags, &temp_a17);

    // Convert back to A16 vector
    out_vec->clear();
    for (const auto& a17_meta : temp_a17) {
        playback_track_metadata_v7_a16 a16_meta;
        memcpy(&a16_meta.base, &a17_meta.base, sizeof(a16_meta.base));
        a16_meta.channel_mask = a17_meta.channel_mask;
        memcpy(a16_meta.tags, a17_meta.tags, sizeof(a16_meta.tags));
        out_vec->push_back(a16_meta);
    }

    return status;
}

#include <aidl/android/hardware/audio/common/PlaybackTrackMetadata.h>
#include <aidl/android/hardware/audio/common/SourceMetadata.h>

namespace v4 {
    struct PlaybackTrackMetadata {
        ::aidl::android::media::audio::common::AudioUsage usage = ::aidl::android::media::audio::common::AudioUsage::INVALID;
        ::aidl::android::media::audio::common::AudioContentType contentType = ::aidl::android::media::audio::common::AudioContentType::UNKNOWN;
        float gain = 0.000000f;
        ::aidl::android::media::audio::common::AudioChannelLayout channelMask;
        std::optional<::aidl::android::media::audio::common::AudioDevice> sourceDevice;
        std::vector<std::string> tags;
    };
    struct SourceMetadata {
        std::vector<PlaybackTrackMetadata> tracks;
    };
}

extern "C" int32_t _ZNK4aidl7android8hardware5audio6common14SourceMetadata13writeToParcelEP7AParcel(const void* this_ptr, void* parcel) {
    auto v4_metadata = reinterpret_cast<const v4::SourceMetadata*>(this_ptr);

    aidl::android::hardware::audio::common::SourceMetadata v5_metadata;
    for (const auto& track : v4_metadata->tracks) {
        aidl::android::hardware::audio::common::PlaybackTrackMetadata v5_track;
        v5_track.usage = track.usage;
        v5_track.contentType = track.contentType;
        v5_track.gain = track.gain;
        v5_track.channelMask = track.channelMask;
        v5_track.sourceDevice = track.sourceDevice;
        v5_track.tags = track.tags;
        v5_metadata.tracks.push_back(v5_track);
    }

    static auto real_func = (int32_t (*)(const void*, void*)) dlsym(RTLD_NEXT, "_ZNK4aidl7android8hardware5audio6common14SourceMetadata13writeToParcelEP7AParcel");
    
    if (!real_func) {
        void* handle = dlopen("android.hardware.audio.common-V5-ndk.so", RTLD_NOW);
        if (handle) {
            real_func = (int32_t (*)(const void*, void*)) dlsym(handle, "_ZNK4aidl7android8hardware5audio6common14SourceMetadata13writeToParcelEP7AParcel");
        }
    }

    if (real_func) {
        return real_func(&v5_metadata, parcel);
    }
    
    ALOGE("Failed to find real SourceMetadata::writeToParcel!");
    return -1;
}