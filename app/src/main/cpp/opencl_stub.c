#include <stdint.h>
#include <stddef.h>

// Minimal OpenCL typedefs for linking
typedef int cl_int;
typedef unsigned int cl_uint;
typedef unsigned long cl_ulong;
typedef void* cl_platform_id;
typedef void* cl_device_id;
typedef void* cl_context;
typedef void* cl_command_queue;
typedef void* cl_mem;
typedef void* cl_program;
typedef void* cl_kernel;
typedef void* cl_event;
typedef struct _cl_image_desc* cl_image_desc;
typedef struct _cl_image_format* cl_image_format;

// Dummy implementation
#define CL_STUB __attribute__((visibility("default")))

CL_STUB cl_int clGetPlatformIDs(cl_uint num_entries, cl_platform_id *platforms, cl_uint *num_platforms) { return -1; }
CL_STUB cl_int clGetDeviceIDs(cl_platform_id platform, cl_ulong device_type, cl_uint num_entries, cl_device_id *devices, cl_uint *num_devices) { return -1; }
CL_STUB cl_int clGetDeviceInfo(cl_device_id device, cl_uint param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return -1; }
CL_STUB cl_context clCreateContext(const void *properties, cl_uint num_devices, const cl_device_id *devices, void *pfn_notify, void *user_data, cl_int *errcode_ret) { return 0; }
CL_STUB cl_command_queue clCreateCommandQueue(cl_context context, cl_device_id device, cl_ulong properties, cl_int *errcode_ret) { return 0; }
CL_STUB cl_program clCreateProgramWithSource(cl_context context, cl_uint count, const char **strings, const size_t *lengths, cl_int *errcode_ret) { return 0; }
CL_STUB cl_int clBuildProgram(cl_program program, cl_uint num_devices, const cl_device_id *device_list, const char *options, void *pfn_notify, void *user_data) { return -1; }
CL_STUB cl_kernel clCreateKernel(cl_program program, const char *kernel_name, cl_int *errcode_ret) { return 0; }
CL_STUB cl_mem clCreateBuffer(cl_context context, cl_ulong flags, size_t size, void *host_ptr, cl_int *errcode_ret) { return 0; }
CL_STUB cl_int clEnqueueWriteBuffer(cl_command_queue command_queue, cl_mem buffer, cl_int blocking_write, size_t offset, size_t size, const void *ptr, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return -1; }
CL_STUB cl_int clEnqueueReadBuffer(cl_command_queue command_queue, cl_mem buffer, cl_int blocking_read, size_t offset, size_t size, void *ptr, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return -1; }
CL_STUB cl_int clEnqueueNDRangeKernel(cl_command_queue command_queue, cl_kernel kernel, cl_uint work_dim, const size_t *global_work_offset, const size_t *global_work_size, const size_t *local_work_size, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return -1; }
CL_STUB cl_int clSetKernelArg(cl_kernel kernel, cl_uint arg_index, size_t arg_size, const void *arg_value) { return -1; }
CL_STUB cl_int clReleaseMemObject(cl_mem memobj) { return -1; }
CL_STUB cl_int clReleaseKernel(cl_kernel kernel) { return -1; }
CL_STUB cl_int clReleaseProgram(cl_program program) { return -1; }
CL_STUB cl_int clReleaseCommandQueue(cl_command_queue command_queue) { return -1; }
CL_STUB cl_int clReleaseContext(cl_context context) { return -1; }
CL_STUB cl_int clFinish(cl_command_queue command_queue) { return -1; }
CL_STUB cl_int clFlush(cl_command_queue command_queue) { return -1; }
CL_STUB cl_int clGetProgramBuildInfo(cl_program program, cl_device_id device, cl_uint param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return -1; }
CL_STUB cl_int clWaitForEvents(cl_uint num_events, const cl_event *event_list) { return -1; }
CL_STUB cl_int clGetEventProfilingInfo(cl_event event, cl_uint param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return -1; }
CL_STUB cl_int clReleaseEvent(cl_event event) { return -1; }
CL_STUB cl_int clRetainEvent(cl_event event) { return -1; }
CL_STUB cl_mem clCreateImage(cl_context context, cl_ulong flags, const cl_image_format *image_format, const cl_image_desc *image_desc, void *host_ptr, cl_int *errcode_ret) { return 0; }
CL_STUB cl_int clEnqueueCopyBuffer(cl_command_queue command_queue, cl_mem src_buffer, cl_mem dst_buffer, size_t src_offset, size_t dst_offset, size_t size, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return -1; }
CL_STUB cl_int clGetKernelWorkGroupInfo(cl_kernel kernel, cl_device_id device, cl_uint param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return -1; }

// Additional symbols required by ggml-opencl
CL_STUB cl_int clGetPlatformInfo(cl_platform_id platform, cl_uint param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return -1; }
CL_STUB cl_mem clCreateSubBuffer(cl_mem buffer, cl_ulong flags, cl_uint buffer_create_type, const void *buffer_create_info, cl_int *errcode_ret) { return 0; }
CL_STUB cl_int clEnqueueBarrierWithWaitList(cl_command_queue command_queue, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return -1; }
CL_STUB cl_int clEnqueueMarkerWithWaitList(cl_command_queue command_queue, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return -1; }
CL_STUB cl_int clEnqueueFillBuffer(cl_command_queue command_queue, cl_mem buffer, const void *pattern, size_t pattern_size, size_t offset, size_t size, cl_uint num_events_in_wait_list, const cl_event *event_wait_list, cl_event *event) { return -1; }
CL_STUB cl_int clGetContextInfo(cl_context context, cl_uint param_name, size_t param_value_size, void *param_value, size_t *param_value_size_ret) { return -1; }
