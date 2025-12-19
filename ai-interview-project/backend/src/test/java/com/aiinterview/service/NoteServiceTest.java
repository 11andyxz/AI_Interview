package com.aiinterview.service;

import com.aiinterview.model.UserNote;
import com.aiinterview.repository.UserNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private UserNoteRepository noteRepository;

    @InjectMocks
    private NoteService noteService;

    private UserNote testNote;
    private Long noteId = 1L;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        testNote = new UserNote();
        testNote.setId(noteId);
        testNote.setUserId(userId);
        testNote.setType("general");
        testNote.setTitle("Test Note");
        testNote.setContent("Test content");
    }

    @Test
    void testGetUserNotes_AllTypes() {
        when(noteRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Arrays.asList(testNote));

        List<UserNote> notes = noteService.getUserNotes(userId, null);

        assertEquals(1, notes.size());
        assertEquals(testNote, notes.get(0));
    }

    @Test
    void testGetUserNotes_ByType() {
        when(noteRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, "interview"))
            .thenReturn(Arrays.asList(testNote));

        List<UserNote> notes = noteService.getUserNotes(userId, "interview");

        assertEquals(1, notes.size());
        verify(noteRepository).findByUserIdAndTypeOrderByCreatedAtDesc(userId, "interview");
    }

    @Test
    void testGetInterviewNotes() {
        String interviewId = "interview-123";
        when(noteRepository.findByUserIdAndInterviewIdOrderByCreatedAtDesc(userId, interviewId))
            .thenReturn(Arrays.asList(testNote));

        List<UserNote> notes = noteService.getInterviewNotes(userId, interviewId);

        assertEquals(1, notes.size());
        verify(noteRepository).findByUserIdAndInterviewIdOrderByCreatedAtDesc(userId, interviewId);
    }

    @Test
    void testGetNoteById_Success() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(testNote));

        Optional<UserNote> result = noteService.getNoteById(noteId, userId);

        assertTrue(result.isPresent());
        assertEquals(testNote, result.get());
    }

    @Test
    void testGetNoteById_WrongUser() {
        Long otherUserId = 2L;
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(testNote));

        Optional<UserNote> result = noteService.getNoteById(noteId, otherUserId);

        assertFalse(result.isPresent());
    }

    @Test
    void testCreateNote() {
        when(noteRepository.save(any(UserNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserNote created = noteService.createNote(userId, "general", "New Note", "Content", null);

        assertNotNull(created);
        assertEquals(userId, created.getUserId());
        assertEquals("general", created.getType());
        assertEquals("New Note", created.getTitle());
        verify(noteRepository).save(any(UserNote.class));
    }

    @Test
    void testUpdateNote_Success() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(testNote));
        when(noteRepository.save(any(UserNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserNote updated = noteService.updateNote(noteId, userId, "Updated Title", "Updated Content");

        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Updated Content", updated.getContent());
        verify(noteRepository).save(any(UserNote.class));
    }

    @Test
    void testUpdateNote_NotFound() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            noteService.updateNote(noteId, userId, "Title", "Content");
        });
    }

    @Test
    void testDeleteNote_Success() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.of(testNote));
        doNothing().when(noteRepository).delete(any(UserNote.class));

        boolean deleted = noteService.deleteNote(noteId, userId);

        assertTrue(deleted);
        verify(noteRepository).delete(testNote);
    }

    @Test
    void testDeleteNote_NotFound() {
        when(noteRepository.findById(noteId)).thenReturn(Optional.empty());

        boolean deleted = noteService.deleteNote(noteId, userId);

        assertFalse(deleted);
        verify(noteRepository, never()).delete(any());
    }
}

