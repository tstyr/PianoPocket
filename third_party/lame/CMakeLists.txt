cmake_minimum_required(VERSION 3.22)
project(PocketLame VERSION 3.100 LANGUAGES C)
# Portable scalar encoder only: decoder, frontend, x86 assembly / SIMD are not linked.
add_library(mp3lame SHARED
 libmp3lame/VbrTag.c libmp3lame/bitstream.c libmp3lame/encoder.c libmp3lame/fft.c
 libmp3lame/gain_analysis.c libmp3lame/id3tag.c libmp3lame/lame.c libmp3lame/newmdct.c
 libmp3lame/presets.c libmp3lame/psymodel.c libmp3lame/quantize.c libmp3lame/quantize_pvt.c
 libmp3lame/reservoir.c libmp3lame/set_get.c libmp3lame/tables.c libmp3lame/takehiro.c
 libmp3lame/util.c libmp3lame/vbrquantize.c libmp3lame/version.c libmp3lame/mpglib_interface.c)
target_include_directories(mp3lame PUBLIC include PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
target_compile_options(mp3lame PRIVATE -O2)
target_link_libraries(mp3lame PRIVATE m)
if(ANDROID)
 target_link_options(mp3lame PRIVATE "-Wl,-z,max-page-size=16384")
endif()
