# Frontend Configuration

## Environment Variables

Create a `.env` file in the frontend root directory with the following variables:

```bash
# API Configuration
REACT_APP_API_BASE_URL=http://localhost:8080
REACT_APP_WS_BASE_URL=http://localhost:8080

# Development settings
GENERATE_SOURCEMAP=false
```

## Default Values

If environment variables are not set, the application will use these defaults:
- API Base URL: `http://localhost:8080`
- WebSocket Base URL: `http://localhost:8080`

## Usage

The API configuration is centralized in `src/utils/api.js`. All API calls should use the exported functions and endpoints from this file to ensure consistency and easy configuration changes.
