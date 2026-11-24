package com.smartteach.admin.repository;

import com.smartteach.admin.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, Short> {
}
