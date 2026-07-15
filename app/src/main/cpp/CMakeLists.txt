cmake_minimum_required(VERSION 3.22.1)
project("nativelens")

add_library(nativelens SHARED
        native_lib.cpp)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(nativelens
        ${log-lib}
        ${jnigraphics-lib})

target_compile_options(nativelens PRIVATE -O3 -ffast-math)
