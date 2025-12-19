package com.aiinterview.repository;

import com.aiinterview.model.UserNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserNoteRepository extends JpaRepository<UserNote, Long> {
    List<UserNote> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<UserNote> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);
    List<UserNote> findByUserIdAndInterviewIdOrderByCreatedAtDesc(Long userId, String interviewId);
}

