package com.fptu.math_master.service;

import com.fptu.math_master.dto.response.MinioBucketResponse;
import com.fptu.math_master.dto.response.MinioObjectListResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface AdminStorageService {

  List<MinioBucketResponse> listBuckets();

  MinioObjectListResponse listObjects(
      String bucket, String prefix, int maxKeys, String continuationToken);

  String presignGetUrl(String bucket, String key);

  void deleteObject(String bucket, String key);

  /** Upload file; optional objectKey overwrites that key. Returns stored object key. */
  String uploadObject(String bucket, String prefix, String objectKey, MultipartFile file);
}
