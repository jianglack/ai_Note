package com.ainote.app.repository;

import com.ainote.app.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, String> {

    List<Tag> findAllByUserId(String userId);

    Optional<Tag> findByIdAndUserId(String id, String userId);

    Optional<Tag> findByNameAndUserId(String name, String userId);
}
