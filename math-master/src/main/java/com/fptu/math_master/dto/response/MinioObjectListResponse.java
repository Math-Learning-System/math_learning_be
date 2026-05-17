package com.fptu.math_master.dto.response;

import java.util.List;

public record MinioObjectListResponse(
    String bucket,
    String prefix,
    List<MinioObjectResponse> objects,
    String nextContinuationToken,
    boolean truncated) {}
