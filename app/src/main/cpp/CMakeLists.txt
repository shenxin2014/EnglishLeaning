cmake_minimum_required(VERSION 3.22.1)
project(listen_whisper LANGUAGES C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
# Pixel 6 and other current arm64 Android devices support these ARMv8.2 instructions.
# The default generic arm64 target omits FP16 vector arithmetic and is substantially slower
# for Whisper's FP16 model.
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)

add_subdirectory(whisper.cpp)

add_library(listenwhisper SHARED whisper_jni.cpp)
target_include_directories(listenwhisper PRIVATE whisper.cpp/include)
target_link_libraries(listenwhisper PRIVATE whisper android log)
