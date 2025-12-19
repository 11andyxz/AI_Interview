import React from 'react';
import { X, Keyboard } from 'lucide-react';
import { KEYBOARD_SHORTCUTS, formatShortcut, isMac } from '../hooks/useKeyboardShortcuts';

const KeyboardShortcutsHelp = ({ isOpen, onClose }) => {
  if (!isOpen) return null;

  // Group shortcuts by category
  const shortcutGroups = {
    'Global Shortcuts': {
      [KEYBOARD_SHORTCUTS.HELP.key]: KEYBOARD_SHORTCUTS.HELP,
      [KEYBOARD_SHORTCUTS.SEARCH.key]: KEYBOARD_SHORTCUTS.SEARCH
    },
    'Interview Room': {
      [KEYBOARD_SHORTCUTS.SEND_MESSAGE.key]: KEYBOARD_SHORTCUTS.SEND_MESSAGE,
      [KEYBOARD_SHORTCUTS.EXIT_INTERVIEW.key]: KEYBOARD_SHORTCUTS.EXIT_INTERVIEW,
      [KEYBOARD_SHORTCUTS.TOGGLE_PAUSE.key]: KEYBOARD_SHORTCUTS.TOGGLE_PAUSE,
      [KEYBOARD_SHORTCUTS.SAVE.key]: KEYBOARD_SHORTCUTS.SAVE
    },
    'Navigation': {
      [KEYBOARD_SHORTCUTS.GO_HOME.key]: KEYBOARD_SHORTCUTS.GO_HOME,
      [KEYBOARD_SHORTCUTS.GO_PROGRESS.key]: KEYBOARD_SHORTCUTS.GO_PROGRESS,
      [KEYBOARD_SHORTCUTS.GO_SKILLS.key]: KEYBOARD_SHORTCUTS.GO_SKILLS,
      [KEYBOARD_SHORTCUTS.GO_SETTINGS.key]: KEYBOARD_SHORTCUTS.GO_SETTINGS
    },
    'Actions': {
      [KEYBOARD_SHORTCUTS.NEW_INTERVIEW.key]: KEYBOARD_SHORTCUTS.NEW_INTERVIEW
    }
  };

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <div
      className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4"
      onClick={handleBackdropClick}
    >
      <div className="bg-white rounded-xl shadow-2xl max-w-2xl w-full max-h-[80vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <div className="flex items-center gap-3">
            <Keyboard className="text-purple-600" size={24} />
            <h2 className="text-xl font-semibold text-gray-900">Keyboard Shortcuts</h2>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 p-1"
          >
            <X size={20} />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 overflow-y-auto max-h-[calc(80vh-140px)]">
          <div className="space-y-6">
            {/* Platform note */}
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
              <p className="text-sm text-blue-800">
                <strong>Note:</strong> On Mac, use <kbd className="px-1 py-0.5 bg-blue-100 rounded text-xs">Cmd</kbd> instead of <kbd className="px-1 py-0.5 bg-blue-100 rounded text-xs">Ctrl</kbd>.
                Press <kbd className="px-1 py-0.5 bg-blue-100 rounded text-xs">?</kbd> at any time to show this help.
              </p>
            </div>

            {/* Shortcut groups */}
            {Object.entries(shortcutGroups).map(([groupName, shortcuts]) => (
              <div key={groupName}>
                <h3 className="text-lg font-medium text-gray-900 mb-3">{groupName}</h3>
                <div className="space-y-2">
                  {Object.entries(shortcuts).map(([key, shortcut]) => (
                    <div
                      key={key}
                      className="flex items-center justify-between py-2 px-3 bg-gray-50 rounded-lg"
                    >
                      <span className="text-sm text-gray-700">{shortcut.description}</span>
                      <kbd className="px-2 py-1 bg-gray-200 text-gray-800 text-xs font-mono rounded border">
                        {formatShortcut(shortcut)}
                      </kbd>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>

          {/* Footer */}
          <div className="mt-6 pt-4 border-t border-gray-200">
            <p className="text-xs text-gray-600 text-center">
              Keyboard shortcuts make it easier to navigate and use the application efficiently.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

// Compact version for tooltips or inline display
export const KeyboardShortcutHint = ({ shortcut, className = '' }) => {
  if (!shortcut) return null;

  return (
    <span className={`text-xs text-gray-500 ${className}`}>
      <kbd className="px-1 py-0.5 bg-gray-100 text-gray-700 rounded text-xs">
        {formatShortcut(shortcut)}
      </kbd>
    </span>
  );
};

export default KeyboardShortcutsHelp;
