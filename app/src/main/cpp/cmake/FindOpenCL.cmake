# Fake FindOpenCL.cmake to force finding our stub and headers
message(STATUS "Using custom FindOpenCL.cmake")

set(OpenCL_FOUND ON)
# OPENCL_HEADERS_PATH is set in top-level CMakeLists.txt
set(OpenCL_INCLUDE_DIR "${OPENCL_HEADERS_PATH}")
set(OpenCL_INCLUDE_DIRS "${OPENCL_HEADERS_PATH}")

if (NOT OpenCL_LIBRARY)
    set(OpenCL_LIBRARY OpenCL_stub)
endif()
set(OpenCL_LIBRARIES ${OpenCL_LIBRARY})

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(OpenCL DEFAULT_MSG OpenCL_INCLUDE_DIR OpenCL_LIBRARY)
