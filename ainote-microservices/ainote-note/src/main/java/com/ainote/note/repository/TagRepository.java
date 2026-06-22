package com.ainote.note.repository;

import com.ainote.note.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, String> {

    Optional<Tag> findByName(String name);

    List<Tag> findByNameIn(List<String> names);

    boolean existsByName(String name);
}
