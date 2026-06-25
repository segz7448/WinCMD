# WinCMD — Windows CMD Emulator for Android

A full Windows Command Prompt emulator for Android, built with pure Java.  
No PC required — push to GitHub and the APK builds automatically.

---

## Features

### CMD Commands (all working)
| Category | Commands |
|---|---|
| File System | `dir`, `cd`, `md`, `mkdir`, `rd`, `rmdir`, `del`, `erase`, `copy`, `move`, `ren`, `rename`, `type`, `more`, `attrib`, `tree`, `xcopy`, `robocopy` |
| Text | `echo`, `find`, `findstr`, `sort`, `fc`, `comp` |
| System | `ver`, `date`, `time`, `set`, `systeminfo`, `wmic`, `reg`, `sfc`, `chkdsk`, `driverquery`, `sc` |
| Network | `ping`, `ipconfig /all`, `netstat`, `net`, `nslookup`, `tracert`, `arp`, `route`, `hostname` |
| Process | `tasklist`, `taskkill /PID`, `taskkill /IM` |
| Misc | `cls`, `help`, `exit`, `color`, `path`, `vol`, `label`, `mode`, `chcp`, `assoc`, `ftype`, `where`, `whoami`, `clip`, `shutdown`, `start` |
| Batch | `if`, `for`, `goto`, `call`, `rem`, `setlocal`, `endlocal`, `pause` |

### Network Commands (real network calls)
- **`ping google.com`** — real ICMP/socket ping, exact Windows output format
- **`ipconfig /all`** — reads real Android network interfaces, shows MAC, IPv4, gateway, DNS
- **`tracert google.com`** — real DNS resolution + simulated hops
- **`nslookup google.com`** — real DNS lookup

### Process Commands (real Android processes)
- **`tasklist`** — reads `/proc` filesystem, shows all running processes as Windows table
- **`taskkill /PID 1234`** — sends kill signal to real process
- **`taskkill /IM name.exe`** — kills process by name

### UI
- Exact Windows CMD look: black background, grey text, monospace font
- Blue title bar with `C:\Windows\System32\cmd.exe`
- Quick toolbar: ↑↓ history, Tab complete, Ctrl+C, common commands
- Output redirect: `dir > list.txt`, `echo hello >> file.txt`
- Environment variable expansion: `echo %USERNAME%`
- Command history (↑↓ keys)

---

## Build via GitHub Actions (no PC needed)

### Setup
1. Push this project to a GitHub repo
2. Go to **Actions** tab → select **Build WinCMD APK** → click **Run workflow**
3. After ~3-5 minutes, download the APK from **Artifacts**

### Auto-build triggers
- Every push to `main`/`master`
- Every pull request
- Manual trigger via workflow_dispatch button

### Optional: Sign release APK
Add these GitHub Secrets (`Settings → Secrets → Actions`):
```
KEYSTORE_BASE64   — base64-encoded .jks keystore
KEY_ALIAS         — key alias
KEY_PASSWORD      — key password
STORE_PASSWORD    — keystore password
```

Generate a keystore on Termux:
```bash
keytool -genkey -v -keystore wincmd.keystore -alias wincmd -keyalg RSA -keysize 2048 -validity 10000
base64 wincmd.keystore | tr -d '\n'   # paste this as KEYSTORE_BASE64
```

---

## Project Structure
```
WinCMD/
├── .github/workflows/build.yml          ← GitHub Actions pipeline
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/zenas/wincmd/
│   │   ├── commands/CmdInterpreter.java  ← All CMD logic
│   │   └── ui/MainActivity.java          ← Terminal UI
│   └── res/
│       ├── layout/activity_main.xml
│       └── values/
├── build.gradle
└── settings.gradle
```

---

## Install on Android
1. Download `WinCMD-debug.apk` from GitHub Actions artifacts
2. Enable **Unknown Sources** in Android settings
3. Install and open — looks exactly like Windows CMD
