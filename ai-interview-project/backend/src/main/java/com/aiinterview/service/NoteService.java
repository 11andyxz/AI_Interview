package com.aiinterview.service;

import com.aiinterview.model.UserNote;
import com.aiinterview.repository.UserNoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NoteService {
    
    @Autowired
    private UserNoteRepository noteRepository;
    
    /**
     * Get all notes for a user
     */
    public List<UserNote> getUserNotes(Long userId, String type) {
        if (type != null && !type.isEmpty()) {
            return noteRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);
        }
        return noteRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Get notes for a specific interview
     */
    public List<UserNote> getInterviewNotes(Long userId, String interviewId) {
        return noteRepository.findByUserIdAndInterviewIdOrderByCreatedAtDesc(userId, interviewId);
    }
    
    /**
     * Get note by ID
     */
    public Optional<UserNote> getNoteById(Long id, Long userId) {
        Optional<UserNote> noteOpt = noteRepository.findById(id);
        if (noteOpt.isPresent() && noteOpt.get().getUserId().equals(userId)) {
            return noteOpt;
        }
        return Optional.empty();
    }
    
    /**
     * Create a new note
     */
    public UserNote createNote(Long userId, String type, String title, String content, String interviewId) {
        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setType(type != null ? type : "general");
        note.setTitle(title);
        note.setContent(content);
        note.setInterviewId(interviewId);
        return noteRepository.save(note);
    }
    
    /**
     * Update a note
     */
    public UserNote updateNote(Long id, Long userId, String title, String content) {
        Optional<UserNote> noteOpt = getNoteById(id, userId);
        if (noteOpt.isEmpty()) {
            throw new RuntimeException("Note not found");
        }
        
        UserNote note = noteOpt.get();
        if (title != null) {
            note.setTitle(title);
        }
        if (content != null) {
            note.setContent(content);
        }
        return noteRepository.save(note);
    }
    
    /**
     * Delete a note
     */
    public boolean deleteNote(Long id, Long userId) {
        Optional<UserNote> noteOpt = getNoteById(id, userId);
        if (noteOpt.isEmpty()) {
            return false;
        }
        noteRepository.delete(noteOpt.get());
        return true;
    }
}

