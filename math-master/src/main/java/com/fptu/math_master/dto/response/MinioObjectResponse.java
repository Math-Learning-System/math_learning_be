package com.fptu.math_master.dto.response;

import java.time.Instant;

public record MinioObjectResponse(
    String key, boolean directory, long size, Instant lastModified) {}
