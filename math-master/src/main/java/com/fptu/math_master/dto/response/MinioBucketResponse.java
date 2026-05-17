package com.fptu.math_master.dto.response;

/** Configured MinIO bucket exposed to admin storage browser. */
public record MinioBucketResponse(String name, String label, String description) {}
