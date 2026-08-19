cmake_minimum_required(VERSION 3.22.1)

project("app" C ASM)

add_library(termux-bootstrap SHARED termux-bootstrap-zip.S termux-bootstrap.c)
target_compile_options(termux-bootstrap PRIVATE -std=c11 -Wall -Wextra -Werror -Os -fno-stack-protector)
target_include_directories(termux-bootstrap PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_options(termux-bootstrap PRIVATE -Wl,--gc-sections)
