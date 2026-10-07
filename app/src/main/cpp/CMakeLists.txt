cmake_minimum_required(VERSION 3.22.1)

project("fimtale" LANGUAGES CXX)

add_library(
        fimtale
        SHARED
        bbcode-syntax.cpp
        bbcode-jni.cpp)

target_compile_features(fimtale PRIVATE cxx_std_17)

if(ANDROID)
    # Support 16 KB memory pages, including builds with older NDKs.
    target_link_options(fimtale PRIVATE
            "-Wl,-z,max-page-size=16384"
            "-Wl,-z,common-page-size=16384")
else()
    # Build the exact JNI implementation for local compatibility tests.
    find_package(JNI REQUIRED)
    target_include_directories(fimtale PRIVATE ${JNI_INCLUDE_DIRS})
endif()
