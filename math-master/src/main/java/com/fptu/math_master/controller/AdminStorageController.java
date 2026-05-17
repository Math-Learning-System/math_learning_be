package com.fptu.math_master.controller;

import com.fptu.math_master.dto.response.ApiResponse;
import com.fptu.math_master.dto.response.MinioBucketResponse;
import com.fptu.math_master.dto.response.MinioObjectListResponse;
import com.fptu.math_master.service.AdminStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/storage")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin — Storage", description = "Browse and manage MinIO objects")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStorageController {

  AdminStorageService adminStorageService;

  @GetMapping("/buckets")
  @Operation(summary = "List configured MinIO buckets")
  public ApiResponse<List<MinioBucketResponse>> listBuckets() {
    return ApiResponse.<List<MinioBucketResponse>>builder()
        .result(adminStorageService.listBuckets())
        .build();
  }

  @GetMapping("/objects")
  @Operation(summary = "List objects in a bucket under a prefix")
  public ApiResponse<MinioObjectListResponse> listObjects(
      @RequestParam String bucket,
      @RequestParam(required = false, defaultValue = "") String prefix,
      @RequestParam(required = false, defaultValue = "50") int maxKeys,
      @RequestParam(required = false) String continuationToken) {
    return ApiResponse.<MinioObjectListResponse>builder()
        .result(adminStorageService.listObjects(bucket, prefix, maxKeys, continuationToken))
        .build();
  }

  @GetMapping("/presign")
  @Operation(summary = "Presigned GET URL for an object")
  public ApiResponse<Map<String, String>> presign(
      @RequestParam String bucket, @RequestParam String key) {
    String url = adminStorageService.presignGetUrl(bucket, key);
    return ApiResponse.<Map<String, String>>builder().result(Map.of("url", url)).build();
  }

  @DeleteMapping("/objects")
  @Operation(summary = "Delete one object")
  public ApiResponse<Void> deleteObject(@RequestParam String bucket, @RequestParam String key) {
    adminStorageService.deleteObject(bucket, key);
    return ApiResponse.<Void>builder().message("Đã xóa").build();
  }

  @PostMapping(value = "/objects/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload or overwrite an object")
  public ApiResponse<Map<String, String>> upload(
      @RequestParam String bucket,
      @RequestParam(required = false) String prefix,
      @RequestParam(required = false) String objectKey,
      @RequestParam("file") MultipartFile file) {
    String storedKey = adminStorageService.uploadObject(bucket, prefix, objectKey, file);
    return ApiResponse.<Map<String, String>>builder()
        .result(Map.of("objectKey", storedKey, "bucket", bucket))
        .build();
  }
}
