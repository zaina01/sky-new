package com.sky.job.web;

public record ApiResponse<T>(int code, String msg, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }
}
