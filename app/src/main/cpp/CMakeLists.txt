cmake_minimum_required(VERSION 3.22.1)

project("fimtale")

add_library(
        fimtale
        SHARED
        native-lib.cpp)

# Support 16 KB memory pages on Android, including builds with older NDKs.
target_link_options(fimtale PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")

find_library(
        log-lib
        log)

target_link_libraries(
        fimtale
        ${log-lib})
