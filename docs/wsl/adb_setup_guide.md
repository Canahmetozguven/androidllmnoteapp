# Guide: Accessing Android Logs (ADB) from WSL

The Android Debug Bridge (`adb`) is installed on the **Windows host**, not inside the WSL container. To capture logs from your S22 or S25, you must bridge this gap.

## 1. Locate ADB on Windows
First, find where `adb.exe` lives on your Windows machine. It's usually in:
`%LOCALAPPDATA%\Android\Sdk\platform-tools\`

**Verify it works in PowerShell:**
```powershell
# Open PowerShell on Windows
cd $env:LOCALAPPDATA\Android\Sdk\platform-tools\
.\adb devices
```
*If this shows your device (e.g., `R5CT... device`), you are ready.*

## 2. Connect from WSL
You can run the Windows executable directly from WSL terminal using its full path.

**Option A: Direct Path (Recommended)**
```bash
# Run this in your WSL terminal
/mnt/c/Users/canahmet/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -c
/mnt/c/Users/canahmet/AppData/Local/Android/Sdk/platform-tools/adb.exe logcat -v color -s HardwareCapability LlmEngine LLM_JNI LLAMA_CPP MainViewModel AndroidRuntime
```

**Option B: Create an Alias (For convenience)**
Add this to your `~/.bashrc` to just type `adb`:
```bash
# Add to .bashrc
export PATH=$PATH:/mnt/c/Users/canahmet/AppData/Local/Android/Sdk/platform-tools
alias adb="adb.exe"
```
Then run `source ~/.bashrc`.

## 3. Capturing Logs for Debugging
To save logs to a file for analysis:

```bash
# In WSL
adb.exe logcat -d > s22_crash_log.txt
```

**Specific filters for Llama.cpp:**
```bash
adb.exe logcat -v time -s HardwareCapability LlmEngine LLM_JNI LLAMA_CPP MainViewModel AndroidRuntime
```

---

**Troubleshooting:**
- **"Device unauthorized"**: Unlock your phone and check for a "Allow USB Debugging" popup.
- **"Command not found"**: You are likely typing `adb` instead of `adb.exe` or the path is wrong. Use `cmd.exe /c "where adb"` in WSL to see if Windows knows where it is.
