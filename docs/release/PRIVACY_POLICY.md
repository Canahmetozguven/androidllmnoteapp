# Privacy Policy for LLM Notes

**Last updated: January 2026**

## Overview

LLM Notes ("the App") is designed with privacy as a core principle. This policy explains what data the App collects, how it is used, and your rights regarding your data.

## Data Collection and Processing

### On-Device Processing
- **AI Processing**: All AI inference and text generation happens entirely on your device using locally-stored models. Your notes and conversations are never sent to external AI servers.
- **Notes Storage**: Your notes are stored locally in the app's private database on your device.
- **Embeddings**: Vector embeddings for semantic search are computed and stored locally on your device.

### Optional Cloud Features
- **Google Drive Backup** (optional): If you choose to enable backup, your notes are synced to your personal Google Drive account. This uses Google's OAuth 2.0 for secure authentication. We only access files within the app's dedicated folder in your Drive.
- **Google Account Email**: When you sign in to enable backup, we display your email address in the app to confirm which account is connected. This is stored locally.

### Analytics (Firebase)
- The App uses Firebase Analytics to collect anonymous usage data such as:
  - App opens and screen views
  - Feature usage patterns
  - Crash reports and performance metrics
- This data is used solely to improve the app experience and does not include your notes content or personal information.

## Data Storage

| Data Type | Storage Location | Shared Externally? |
|-----------|------------------|-------------------|
| Notes content | Device only (or Google Drive if backup enabled) | No |
| AI model files | Device only | No |
| Chat history | Device only | No |
| Analytics events | Firebase (anonymized) | Google Analytics only |
| Google account email | Device only | No |

## Data Sharing

We do not sell, trade, or share your personal data with third parties, except:
- **Google Drive**: Only if you explicitly enable backup, and only to your own account.
- **Firebase Analytics**: Anonymous usage statistics (no personal content).

## Your Rights

- **Delete Data**: You can delete all your notes and chat history from within the app at any time.
- **Disable Backup**: You can disconnect Google Drive backup at any time from Settings.
- **Uninstall**: Uninstalling the app removes all locally stored data.

## Model Downloads

When you download AI models through the app:
- Models are downloaded from Hugging Face (huggingface.co)
- Downloads are stored locally on your device
- No account or personal information is shared during downloads

## Children's Privacy

This App is not intended for use by children under 13 years of age. We do not knowingly collect personal information from children.

## Changes to This Policy

We may update this Privacy Policy from time to time. We will notify you of any changes by updating the "Last updated" date at the top of this policy.

## Contact

If you have questions about this Privacy Policy, please open an issue on our GitHub repository or contact us through the app store listing.

---

**Summary**: Your notes stay on your device. AI runs locally. Backup to your own Google Drive is optional. Anonymous analytics help us improve the app.
