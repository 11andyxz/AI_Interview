import React from 'react';

const LoadingSkeleton = ({
  className = '',
  variant = 'default',
  lines = 1,
  width = '100%',
  height = '1rem',
  animated = true
}) => {
  const baseClasses = 'bg-gray-200 rounded';
  const animationClasses = animated ? 'animate-pulse' : '';

  if (variant === 'card') {
    return (
      <div className={`border border-gray-200 rounded-lg p-4 ${animationClasses} ${className}`}>
        <div className="flex items-center space-x-4">
          <div className="w-12 h-12 bg-gray-200 rounded-full"></div>
          <div className="flex-1 space-y-2">
            <div className="h-4 bg-gray-200 rounded w-3/4"></div>
            <div className="h-4 bg-gray-200 rounded w-1/2"></div>
          </div>
        </div>
      </div>
    );
  }

  if (variant === 'interview-card') {
    return (
      <div className={`border border-gray-200 rounded-lg p-4 ${animationClasses} ${className}`}>
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div className="h-5 bg-gray-200 rounded w-1/2"></div>
            <div className="h-4 bg-gray-200 rounded w-16"></div>
          </div>
          <div className="h-4 bg-gray-200 rounded w-3/4"></div>
          <div className="flex items-center justify-between">
            <div className="h-3 bg-gray-200 rounded w-20"></div>
            <div className="h-3 bg-gray-200 rounded w-24"></div>
          </div>
          <div className="flex space-x-2">
            <div className="h-8 bg-gray-200 rounded w-20"></div>
            <div className="h-8 bg-gray-200 rounded w-20"></div>
          </div>
        </div>
      </div>
    );
  }

  if (variant === 'chart') {
    return (
      <div className={`border border-gray-200 rounded-lg p-6 ${animationClasses} ${className}`}>
        <div className="h-6 bg-gray-200 rounded w-1/3 mb-4"></div>
        <div className="h-64 bg-gray-200 rounded"></div>
      </div>
    );
  }

  if (variant === 'table') {
    return (
      <div className={`border border-gray-200 rounded-lg ${animationClasses} ${className}`}>
        <div className="p-4 border-b border-gray-200">
          <div className="h-5 bg-gray-200 rounded w-1/4"></div>
        </div>
        {Array.from({ length: lines }, (_, i) => (
          <div key={i} className="p-4 border-b border-gray-200 last:border-b-0">
            <div className="flex items-center space-x-4">
              <div className="h-4 bg-gray-200 rounded flex-1"></div>
              <div className="h-4 bg-gray-200 rounded w-20"></div>
              <div className="h-4 bg-gray-200 rounded w-16"></div>
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (variant === 'text') {
    return (
      <div className={`space-y-2 ${className}`}>
        {Array.from({ length: lines }, (_, i) => (
          <div
            key={i}
            className={`bg-gray-200 rounded ${animationClasses}`}
            style={{
              width: Array.isArray(width) ? width[i % width.length] : width,
              height: Array.isArray(height) ? height[i % height.length] : height
            }}
          />
        ))}
      </div>
    );
  }

  // Default skeleton
  return (
    <div
      className={`${baseClasses} ${animationClasses} ${className}`}
      style={{ width, height }}
    />
  );
};

// Predefined skeleton components for common use cases
export const InterviewCardSkeleton = ({ count = 3 }) => (
  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
    {Array.from({ length: count }, (_, i) => (
      <LoadingSkeleton key={i} variant="interview-card" />
    ))}
  </div>
);

export const DashboardSkeleton = () => (
  <div className="space-y-6">
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      <LoadingSkeleton className="h-32" />
      <LoadingSkeleton className="h-32" />
      <LoadingSkeleton className="h-32" />
    </div>
    <div className="grid md:grid-cols-2 gap-6">
      <LoadingSkeleton variant="chart" />
      <LoadingSkeleton variant="chart" />
    </div>
  </div>
);

export const ReportSkeleton = () => (
  <div className="space-y-6">
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      <LoadingSkeleton variant="chart" />
      <LoadingSkeleton variant="chart" />
    </div>
    <LoadingSkeleton variant="text" lines={4} />
    <LoadingSkeleton variant="table" lines={5} />
  </div>
);

export const SkillProgressSkeleton = () => (
  <div className="space-y-6">
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      {Array.from({ length: 4 }, (_, i) => (
        <LoadingSkeleton key={i} className="h-24" />
      ))}
    </div>
    <div className="grid md:grid-cols-2 gap-6">
      <LoadingSkeleton variant="chart" />
      <LoadingSkeleton variant="chart" />
    </div>
  </div>
);

export default LoadingSkeleton;
