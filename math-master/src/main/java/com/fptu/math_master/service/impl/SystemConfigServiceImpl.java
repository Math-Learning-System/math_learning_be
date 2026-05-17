package com.fptu.math_master.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptu.math_master.dto.request.UpdateSystemConfigRequest;
import com.fptu.math_master.dto.response.SystemConfigResponse;
import com.fptu.math_master.entity.SystemConfig;
import com.fptu.math_master.exception.AppException;
import com.fptu.math_master.exception.ErrorCode;
import com.fptu.math_master.repository.SystemConfigRepository;
import com.fptu.math_master.service.AssessmentImportConfigService;
import com.fptu.math_master.service.SystemConfigService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemConfigServiceImpl implements SystemConfigService {

    SystemConfigRepository systemConfigRepository;
    AssessmentImportConfigService assessmentImportConfigService;

    @Override
    @Transactional(readOnly = true)
    public SystemConfigResponse getByKey(String key) {
        SystemConfig config = systemConfigRepository
                .findByConfigKeyAndDeletedAtIsNull(key)
                .orElseThrow(() -> new AppException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));
        return toResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResponse> listAll() {
        return systemConfigRepository.findAllByDeletedAtIsNull()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SystemConfigResponse update(String key, UpdateSystemConfigRequest request, UUID updatedBy) {
        SystemConfig config = systemConfigRepository
                .findByConfigKeyAndDeletedAtIsNull(key)
                .orElseThrow(() -> new AppException(ErrorCode.SYSTEM_CONFIG_NOT_FOUND));

        if (AssessmentImportConfigService.CONFIG_KEY.equals(key)) {
            assessmentImportConfigService.validateOptionsJson(request.configValue());
        }

        config.setConfigValue(request.configValue());
        config.setUpdatedBy(updatedBy);

        return toResponse(systemConfigRepository.save(config));
    }

    private SystemConfigResponse toResponse(SystemConfig config) {
        return new SystemConfigResponse(
                config.getConfigKey(),
                config.getConfigValue(),
                config.getDescription(),
                config.getUpdatedAt()
        );
    }
}
