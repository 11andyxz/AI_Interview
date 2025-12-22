import { useEffect, useCallback } from 'react';

// Keyboard shortcuts configuration
export const KEYBOARD_SHORTCUTS = {
  // Global shortcuts
  HELP: { key: '?', ctrl: true, description: 'Show keyboard shortcuts help' },
  SEARCH: { key: 'k', ctrl: true, description: 'Focus search input' },

  // Interview room shortcuts
  SEND_MESSAGE: { key: 'Enter', description: 'Send message (when typing)' },
  EXIT_INTERVIEW: { key: 'Escape', description: 'Exit interview' },
  TOGGLE_PAUSE: { key: ' ', description: 'Pause/Resume interview' },

  // Navigation shortcuts
  GO_HOME: { key: 'h', alt: true, description: 'Go to Dashboard' },
  GO_PROGRESS: { key: 'p', alt: true, description: 'Go to Progress' },
  GO_SKILLS: { key: 's', alt: true, description: 'Go to Skills' },
  GO_SETTINGS: { key: 't', alt: true, description: 'Go to Settings' },

  // Action shortcuts
  NEW_INTERVIEW: { key: 'n', ctrl: true, shift: true, description: 'Create new interview' },
  SAVE: { key: 's', ctrl: true, description: 'Save current work' },
};

// Utility function to check if event matches shortcut
export const matchesShortcut = (event, shortcut) => {
  // Guard against undefined values
  if (!event || !shortcut || !event.key || !shortcut.key) {
    return false;
  }

  const { key, ctrl = false, shift = false, alt = false, meta = false } = shortcut;

  // Convert keys to lowercase for comparison, handling special keys
  const eventKey = event.key.toLowerCase();
  const shortcutKey = key.toLowerCase();

  return (
    eventKey === shortcutKey &&
    event.ctrlKey === ctrl &&
    event.shiftKey === shift &&
    event.altKey === alt &&
    event.metaKey === meta
  );
};

// Hook for managing keyboard shortcuts
export const useKeyboardShortcuts = (shortcuts = {}) => {
  const handleKeyDown = useCallback((event) => {
    // Find matching shortcut
    for (const [action, shortcut] of Object.entries(shortcuts)) {
      if (matchesShortcut(event, shortcut)) {
        event.preventDefault();
        event.stopPropagation();

        // Call the handler
        if (shortcut.handler) {
          shortcut.handler(event);
        }

        break;
      }
    }
  }, [shortcuts]);

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  return { shortcuts };
};

// Pre-configured hooks for specific contexts

// Interview room shortcuts
export const useInterviewShortcuts = (handlers = {}) => {
  const shortcuts = {
    SEND_MESSAGE: {
      ...KEYBOARD_SHORTCUTS.SEND_MESSAGE,
      handler: handlers.onSend || (() => {})
    },
    EXIT_INTERVIEW: {
      ...KEYBOARD_SHORTCUTS.EXIT_INTERVIEW,
      handler: handlers.onExit || (() => {})
    },
    TOGGLE_PAUSE: {
      ...KEYBOARD_SHORTCUTS.TOGGLE_PAUSE,
      handler: handlers.onTogglePause || (() => {})
    },
    SAVE: {
      ...KEYBOARD_SHORTCUTS.SAVE,
      handler: handlers.onSave || (() => {})
    }
  };

  return useKeyboardShortcuts(shortcuts);
};

// Dashboard shortcuts
export const useDashboardShortcuts = (handlers = {}) => {
  const shortcuts = {
    SEARCH: {
      ...KEYBOARD_SHORTCUTS.SEARCH,
      handler: handlers.onSearch || (() => {})
    },
    NEW_INTERVIEW: {
      ...KEYBOARD_SHORTCUTS.NEW_INTERVIEW,
      handler: handlers.onNewInterview || (() => {})
    },
    HELP: {
      ...KEYBOARD_SHORTCUTS.HELP,
      handler: handlers.onHelp || (() => {})
    }
  };

  return useKeyboardShortcuts(shortcuts);
};

// Global shortcuts (available everywhere)
export const useGlobalShortcuts = (handlers = {}) => {
  const shortcuts = {
    HELP: {
      ...KEYBOARD_SHORTCUTS.HELP,
      handler: handlers.onHelp || (() => {})
    },
    GO_HOME: {
      ...KEYBOARD_SHORTCUTS.GO_HOME,
      handler: handlers.onGoHome || (() => {})
    },
    GO_PROGRESS: {
      ...KEYBOARD_SHORTCUTS.GO_PROGRESS,
      handler: handlers.onGoProgress || (() => {})
    },
    GO_SKILLS: {
      ...KEYBOARD_SHORTCUTS.GO_SKILLS,
      handler: handlers.onGoSkills || (() => {})
    },
    GO_SETTINGS: {
      ...KEYBOARD_SHORTCUTS.GO_SETTINGS,
      handler: handlers.onGoSettings || (() => {})
    }
  };

  return useKeyboardShortcuts(shortcuts);
};

// Utility functions
export const formatShortcut = (shortcut) => {
  if (!shortcut || !shortcut.key) {
    return '';
  }

  const parts = [];

  if (shortcut.ctrl) parts.push('Ctrl');
  if (shortcut.meta) parts.push('Cmd');
  if (shortcut.alt) parts.push('Alt');
  if (shortcut.shift) parts.push('Shift');

  parts.push(shortcut.key.toUpperCase());

  return parts.join(' + ');
};

export const getShortcutDescription = (shortcut) => {
  return shortcut.description || '';
};

export const isMac = () => navigator.platform.toUpperCase().indexOf('MAC') >= 0;

export default useKeyboardShortcuts;
