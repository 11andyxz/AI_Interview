import React, { useState } from 'react';
import { HelpCircle, X } from 'lucide-react';

const HelpTooltip = ({
  content,
  title = "Help",
  position = "top",
  children,
  className = "",
  iconSize = 16
}) => {
  const [isVisible, setIsVisible] = useState(false);

  const getPositionClasses = () => {
    switch (position) {
      case 'top':
        return 'bottom-full left-1/2 transform -translate-x-1/2 mb-2';
      case 'bottom':
        return 'top-full left-1/2 transform -translate-x-1/2 mt-2';
      case 'left':
        return 'right-full top-1/2 transform -translate-y-1/2 mr-2';
      case 'right':
        return 'left-full top-1/2 transform -translate-y-1/2 ml-2';
      default:
        return 'bottom-full left-1/2 transform -translate-x-1/2 mb-2';
    }
  };

  const getArrowClasses = () => {
    switch (position) {
      case 'top':
        return 'top-full left-1/2 transform -translate-x-1/2 border-l-transparent border-r-transparent border-b-transparent';
      case 'bottom':
        return 'bottom-full left-1/2 transform -translate-x-1/2 border-l-transparent border-r-transparent border-t-transparent';
      case 'left':
        return 'left-full top-1/2 transform -translate-y-1/2 border-t-transparent border-b-transparent border-r-transparent';
      case 'right':
        return 'right-full top-1/2 transform -translate-y-1/2 border-t-transparent border-b-transparent border-l-transparent';
      default:
        return 'top-full left-1/2 transform -translate-x-1/2 border-l-transparent border-r-transparent border-b-transparent';
    }
  };

  return (
    <div className={`relative inline-block ${className}`}>
      <div
        className="cursor-help inline-flex items-center justify-center"
        onMouseEnter={() => setIsVisible(true)}
        onMouseLeave={() => setIsVisible(false)}
        onClick={() => setIsVisible(!isVisible)}
      >
        {children || (
          <HelpCircle
            size={iconSize}
            className="text-gray-400 hover:text-gray-600 transition-colors"
          />
        )}
      </div>

      {isVisible && (
        <>
          {/* Backdrop */}
          <div
            className="fixed inset-0 z-40"
            onClick={() => setIsVisible(false)}
          />

          {/* Tooltip */}
          <div
            className={`absolute z-50 ${getPositionClasses()} bg-white border border-gray-200 rounded-lg shadow-lg p-4 max-w-xs w-max`}
          >
            {/* Arrow */}
            <div
              className={`absolute w-0 h-0 border-4 border-gray-200 ${getArrowClasses()}`}
              style={{
                borderWidth: '6px',
                ...(position === 'top' && { borderTopColor: 'white', top: '-1px' }),
                ...(position === 'bottom' && { borderBottomColor: 'white', bottom: '-1px' }),
                ...(position === 'left' && { borderLeftColor: 'white', left: '-1px' }),
                ...(position === 'right' && { borderRightColor: 'white', right: '-1px' }),
              }}
            />

            {/* Close button */}
            <button
              onClick={() => setIsVisible(false)}
              className="absolute top-2 right-2 text-gray-400 hover:text-gray-600"
            >
              <X size={14} />
            </button>

            {/* Content */}
            {title && (
              <h4 className="font-semibold text-gray-900 mb-2 pr-6">{title}</h4>
            )}
            <div className="text-sm text-gray-700 pr-6">
              {content}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default HelpTooltip;
