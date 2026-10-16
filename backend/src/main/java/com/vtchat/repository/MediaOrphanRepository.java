package com.vtchat.repository;

import com.vtchat.entity.MediaOrphan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaOrphanRepository extends JpaRepository<MediaOrphan, Long> {
}
