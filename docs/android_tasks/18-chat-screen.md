# Task 18 - Chat Screen

## Goal
Provide AI chat interface with conversation history.

## Reference
- `src/components/AIChat.tsx`

## Steps
1. Create `ChatScreen` Compose UI:
   - Message list with user/AI bubbles.
   - Input box + send button.
2. Store chat history in ViewModel state.
3. Stream model output into the list (append tokens incrementally).

## Output
- User can chat with local model.

## Notes
Consider throttling UI updates for long responses.
