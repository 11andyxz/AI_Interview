# Test Run Report – 2025/12/10

## 1. Resume Summary Test
- Test case loaded in ai_expected_behavior.md
- Since backend is not connected to cloud MySQL yet, API returns 400/404. This is expected.
- Database test data inserted successfully.

## 2. Interview Creation Test
- Frontend POST /api/interview/create now returns 400/404 because backend is not yet fully connected.
- This behavior is expected at current stage.

## 3. Chat Flow Test
- Mock API not connected, so WebSocket and chat endpoints fail as expected.
- No issue from frontend side.

## 4. Database Validation
- All required tables created: candidate, interview, interview_message
- Test data inserted and verified with SELECT queries.

## 5. Notes
- Current environment does not integrate backend with cloud MySQL.
- Today's task focuses only on test case design + SQL test data.
- System integration will be handled separately after backend connection is configured.
