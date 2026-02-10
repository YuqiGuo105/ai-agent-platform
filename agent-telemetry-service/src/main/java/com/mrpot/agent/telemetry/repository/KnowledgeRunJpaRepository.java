package com.mrpot.agent.telemetry.repository;

import com.mrpot.agent.telemetry.entity.KnowledgeRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeRunJpaRepository extends JpaRepository<KnowledgeRunEntity, String> {}
