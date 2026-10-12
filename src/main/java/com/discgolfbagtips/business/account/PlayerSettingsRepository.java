package com.discgolfbagtips.business.account;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerSettingsRepository extends JpaRepository<PlayerSettingsEntity, String> {
}
