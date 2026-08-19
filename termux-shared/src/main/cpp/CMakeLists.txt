cmake_minimum_required(VERSION 3.22.1)

project("termux-shared")

add_library(local-socket SHARED local-socket.cpp)
target_link_libraries(local-socket log)
