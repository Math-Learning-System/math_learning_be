package com.fptu.math_master.service.impl;

import com.fptu.math_master.configuration.properties.MinioProperties;
import com.fptu.math_master.dto.response.MinioBucketResponse;
import com.fptu.math_master.dto.response.MinioObjectListResponse;
import com.fptu.math_master.dto.response.MinioObjectResponse;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.service.AdminStorageService;
import com.fptu.math_master.service.UploadService;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminStorageServiceImpl implements AdminStorageService {

  private static final int MAX_KEYS_CAP = 200;

  private final MinioClient minioClient;
  private final MinioProperties minioProperties;
  private final UploadService uploadService;

  @Override
  public List<MinioBucketResponse> listBuckets() {
    Set<String> names = new LinkedHashSet<>();
    names.add(minioProperties.getTemplateBucket());
    names.add(minioProperties.getVerificationBucket());
    names.add(minioProperties.getCourseVideosBucket());
    names.add(minioProperties.getCourseMaterialsBucket());
    if (StringUtils.hasText(minioProperties.getOcrContentBucket())) {
      names.add(minioProperties.getOcrContentBucket());
    }

    List<MinioBucketResponse> result = new ArrayList<>();
    for (String name : names) {
      result.add(new MinioBucketResponse(name, labelForBucket(name), descriptionForBucket(name)));
    }
    return result;
  }

  @Override
  public MinioObjectListResponse listObjects(
      String bucket, String prefix, int maxKeys, String continuationToken) {
    String resolvedBucket = resolveBucket(bucket);
    ensureBucketExists(resolvedBucket);

    String normalizedPrefix = normalizePrefix(prefix);
    int limit = Math.min(Math.max(maxKeys, 1), MAX_KEYS_CAP);

    List<MinioObjectResponse> objects = new ArrayList<>();
    String nextToken = null;
    boolean truncated = false;

    try {
      Iterable<Result<Item>> results =
          minioClient.listObjects(
              ListObjectsArgs.builder()
                  .bucket(resolvedBucket)
                  .prefix(normalizedPrefix)
                  .delimiter("/")
                  .maxKeys(limit + 1)
                  .startAfter(
                      StringUtils.hasText(continuationToken) ? continuationToken.trim() : null)
                  .build());

      for (Result<Item> result : results) {
        Item item = result.get();
        if (item == null) {
          continue;
        }
        String key = item.objectName();
        if (!StringUtils.hasText(key)) {
          continue;
        }
        if (objects.size() >= limit) {
          truncated = true;
          nextToken = objects.get(objects.size() - 1).key();
          break;
        }
        boolean directory = item.isDir() || key.endsWith("/");
        long size = directory ? 0L : safeSize(item);
        objects.add(
            new MinioObjectResponse(key, directory, size, safeLastModified(item)));
      }
    } catch (Exception ex) {
      log.error("Failed to list MinIO objects bucket={} prefix={}", resolvedBucket, normalizedPrefix, ex);
      throw new AppException(ErrorCode.INVALID_KEY, "Không liệt kê được file MinIO: " + ex.getMessage());
    }

    return new MinioObjectListResponse(
        resolvedBucket, normalizedPrefix, objects, nextToken, truncated);
  }

  @Override
  public String presignGetUrl(String bucket, String key) {
    String resolvedBucket = resolveBucket(bucket);
    if (!StringUtils.hasText(key)) {
      throw new AppException(ErrorCode.INVALID_KEY, "Thiếu object key");
    }
    return uploadService.getPresignedUrl(key.trim(), resolvedBucket);
  }

  @Override
  public void deleteObject(String bucket, String key) {
    String resolvedBucket = resolveBucket(bucket);
    if (!StringUtils.hasText(key)) {
      throw new AppException(ErrorCode.INVALID_KEY, "Thiếu object key");
    }
    String normalizedKey = key.trim();
    if (normalizedKey.endsWith("/")) {
      throw new AppException(ErrorCode.INVALID_KEY, "Không thể xóa thư mục — chỉ xóa từng file.");
    }
    try {
      minioClient.removeObject(
          io.minio.RemoveObjectArgs.builder()
              .bucket(resolvedBucket)
              .object(normalizedKey)
              .build());
      log.info("Admin deleted MinIO object {}/{}", resolvedBucket, normalizedKey);
    } catch (Exception ex) {
      log.error("Admin delete MinIO failed {}/{}", resolvedBucket, normalizedKey, ex);
      throw new AppException(ErrorCode.INVALID_KEY, "Xóa file thất bại: " + ex.getMessage());
    }
  }

  @Override
  public String uploadObject(String bucket, String prefix, String objectKey, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new AppException(ErrorCode.INVALID_KEY, "File rỗng");
    }
    String resolvedBucket = resolveBucket(bucket);
    ensureBucketExists(resolvedBucket);

    String targetKey;
    if (StringUtils.hasText(objectKey)) {
      targetKey = objectKey.trim();
      while (targetKey.startsWith("/")) {
        targetKey = targetKey.substring(1);
      }
    } else {
      String dir = normalizePrefix(prefix);
      String original =
          StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
      String extension = "";
      int dot = original.lastIndexOf('.');
      if (dot > 0) {
        extension = original.substring(dot);
      }
      targetKey = dir + UUID.randomUUID() + extension;
    }

    try (InputStream stream = file.getInputStream()) {
      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(resolvedBucket)
              .object(targetKey)
              .stream(stream, file.getSize(), -1)
              .contentType(file.getContentType())
              .build());
      log.info("Admin uploaded MinIO object {}/{}", resolvedBucket, targetKey);
      return targetKey;
    } catch (Exception ex) {
      log.error("Admin upload MinIO failed bucket={} key={}", resolvedBucket, targetKey, ex);
      throw new AppException(ErrorCode.INVALID_KEY, "Tải lên thất bại: " + ex.getMessage());
    }
  }

  private String resolveBucket(String bucket) {
    if (!StringUtils.hasText(bucket)) {
      return minioProperties.getTemplateBucket();
    }
    String name = bucket.trim();
    for (MinioBucketResponse b : listBuckets()) {
      if (b.name().equals(name)) {
        return name;
      }
    }
    throw new AppException(ErrorCode.INVALID_KEY, "Bucket không được phép: " + name);
  }

  private void ensureBucketExists(String bucketName) {
    try {
      boolean found =
          minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
      if (!found) {
        throw new AppException(
            ErrorCode.INVALID_KEY, "Bucket chưa tồn tại trên MinIO: " + bucketName);
      }
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException(ErrorCode.CRAWLER_UNAVAILABLE, "Không kết nối được MinIO");
    }
  }

  /** MinIO prefix/directory rows often have no lastModified — avoid NPE in ResponseDate. */
  private static Instant safeLastModified(Item item) {
    try {
      var modified = item.lastModified();
      if (modified == null) {
        return null;
      }
      return modified.toInstant();
    } catch (Exception ex) {
      return null;
    }
  }

  private static long safeSize(Item item) {
    try {
      return item.size();
    } catch (Exception ex) {
      return 0L;
    }
  }

  private static String normalizePrefix(String prefix) {
    if (!StringUtils.hasText(prefix)) {
      return "";
    }
    String p = prefix.trim().replace('\\', '/');
    while (p.startsWith("/")) {
      p = p.substring(1);
    }
    return p;
  }

  private String labelForBucket(String name) {
    if (name.equals(minioProperties.getTemplateBucket())) {
      return "Slide & đề PDF";
    }
    if (name.equals(minioProperties.getVerificationBucket())) {
      return "Xác thực giáo viên";
    }
    if (name.equals(minioProperties.getCourseVideosBucket())) {
      return "Video khóa học";
    }
    if (name.equals(minioProperties.getCourseMaterialsBucket())) {
      return "Tài liệu khóa học";
    }
    if (name.equals(minioProperties.getOcrContentBucket())) {
      return "OCR sách";
    }
    return name;
  }

  private String descriptionForBucket(String name) {
    if (name.equals(minioProperties.getTemplateBucket())) {
      return "Slide template, PDF import đề (assessments/pdf-imports/…), thumbnail khóa học";
    }
    if (name.equals(minioProperties.getVerificationBucket())) {
      return "Hồ sơ xác thực giáo viên (ZIP/ảnh)";
    }
    if (name.equals(minioProperties.getCourseVideosBucket())) {
      return "Video bài học";
    }
    if (name.equals(minioProperties.getCourseMaterialsBucket())) {
      return "PDF/tài liệu đính kèm bài học";
    }
    if (name.equals(minioProperties.getOcrContentBucket())) {
      return "Ảnh trang sách OCR, PDF sách (books/pdfs)";
    }
    return "";
  }
}
