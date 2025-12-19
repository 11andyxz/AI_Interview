package com.aiinterview.controller;

import com.aiinterview.model.UserNote;
import com.aiinterview.service.NoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NoteController.class)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NoteService noteService;

    private Long userId = 1L;
    private Long noteId = 1L;
    private UserNote testNote;

    @BeforeEach
    void setUp() {
        testNote = new UserNote();
        testNote.setId(noteId);
        testNote.setUserId(userId);
        testNote.setType("general");
        testNote.setTitle("Test Note");
        testNote.setContent("Test content");
        testNote.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testGetNotes_Success() throws Exception {
        when(noteService.getUserNotes(userId, null)).thenReturn(Arrays.asList(testNote));

        mockMvc.perform(get("/api/notes")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(noteId))
            .andExpect(jsonPath("$[0].title").value("Test Note"));
    }

    @Test
    void testGetNotes_ByType() throws Exception {
        when(noteService.getUserNotes(userId, "interview")).thenReturn(Arrays.asList(testNote));

        mockMvc.perform(get("/api/notes")
                .param("type", "interview")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk());
    }

    @Test
    void testGetNoteById_Success() throws Exception {
        when(noteService.getNoteById(noteId, userId)).thenReturn(Optional.of(testNote));

        mockMvc.perform(get("/api/notes/" + noteId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(noteId));
    }

    @Test
    void testGetNoteById_NotFound() throws Exception {
        when(noteService.getNoteById(noteId, userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/notes/" + noteId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isNotFound());
    }

    @Test
    void testCreateNote_Success() throws Exception {
        when(noteService.createNote(eq(userId), anyString(), anyString(), anyString(), any()))
            .thenReturn(testNote);

        mockMvc.perform(post("/api/notes")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Test Note\",\"content\":\"Content\",\"type\":\"general\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testCreateNote_MissingTitle() throws Exception {
        mockMvc.perform(post("/api/notes")
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Content\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateNote_Success() throws Exception {
        when(noteService.getNoteById(noteId, userId)).thenReturn(Optional.of(testNote));
        when(noteService.updateNote(eq(noteId), eq(userId), anyString(), anyString()))
            .thenReturn(testNote);

        mockMvc.perform(put("/api/notes/" + noteId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated Title\",\"content\":\"Updated Content\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testDeleteNote_Success() throws Exception {
        when(noteService.deleteNote(noteId, userId)).thenReturn(true);

        mockMvc.perform(delete("/api/notes/" + noteId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testGetInterviewNotes_Success() throws Exception {
        String interviewId = "interview-123";
        testNote.setInterviewId(interviewId);
        when(noteService.getInterviewNotes(userId, interviewId))
            .thenReturn(Arrays.asList(testNote));

        mockMvc.perform(get("/api/notes/interview/{interviewId}", interviewId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(noteId))
            .andExpect(jsonPath("$[0].interviewId").value(interviewId));
    }

    @Test
    void testGetInterviewNotes_Empty() throws Exception {
        String interviewId = "interview-123";
        when(noteService.getInterviewNotes(userId, interviewId))
            .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/notes/interview/{interviewId}", interviewId)
                .header("Authorization", "Bearer valid-token")
                .requestAttr("userId", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetInterviewNotes_Unauthorized() throws Exception {
        String interviewId = "interview-123";
        mockMvc.perform(get("/api/notes/interview/{interviewId}", interviewId))
            .andExpect(status().isUnauthorized());
    }
}

