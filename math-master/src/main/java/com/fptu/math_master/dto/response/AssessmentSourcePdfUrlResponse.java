package com.fptu.math_master.dto.response;

/** Presigned URL to view/download the PDF uploaded via import (Cách 2). */
public record AssessmentSourcePdfUrlResponse(String url, String fileName) {}
