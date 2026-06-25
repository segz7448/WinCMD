package com.zenas.wincmd.commands;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CmdInterpreter {

    private final Context context;
    private File currentDir;
    private final Map<String, String> envVars = new HashMap<>();
    private final List<String> commandHistory = new ArrayList<>();

    // Callback for async output (ping, etc.)
    public interface OutputCallback {
        void onOutput(String text);
        void onDone(String prompt);
        void onError(String text);
    }

    public CmdInterpreter(Context context) {
        this.context = context;
        // Start in a fake Windows-like path backed by Android storage
        currentDir = context.getExternalFilesDir(null);
        if (currentDir == null) currentDir = context.getFilesDir();

        // Default Windows environment variables
        envVars.put("COMPUTERNAME", "DESKTOP-" + Build.MODEL.toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0, Math.min(6, Build.MODEL.length())));
        envVars.put("USERNAME", "User");
        envVars.put("OS", "Windows_NT");
        envVars.put("PROCESSOR_ARCHITECTURE", "AMD64");
        envVars.put("SystemRoot", "C:\\Windows");
        envVars.put("SystemDrive", "C:");
        envVars.put("USERPROFILE", "C:\\Users\\User");
        envVars.put("APPDATA", "C:\\Users\\User\\AppData\\Roaming");
        envVars.put("TEMP", "C:\\Users\\User\\AppData\\Local\\Temp");
        envVars.put("TMP", "C:\\Users\\User\\AppData\\Local\\Temp");
        envVars.put("PATHEXT", ".COM;.EXE;.BAT;.CMD;.VBS;.VBE;.JS;.JSE;.WSF;.WSH;.MSC");
        envVars.put("COMSPEC", "C:\\Windows\\System32\\cmd.exe");
        envVars.put("NUMBER_OF_PROCESSORS", String.valueOf(Runtime.getRuntime().availableProcessors()));
        envVars.put("PROCESSOR_IDENTIFIER", "Intel64 Family 6 Model 94 Stepping 3, GenuineIntel");
    }

    public String getWindowsPath() {
        // Map real android path to a fake Windows path for display
        return "C:\\Users\\User";
    }

    public String getPrompt() {
        return getWindowsPath() + ">";
    }

    /**
     * Main entry point. Returns output string for sync commands.
     * Returns null and calls callback for async commands (ping).
     */
    public String execute(String input, OutputCallback asyncCallback) {
        if (input == null || input.trim().isEmpty()) return "";
        commandHistory.add(input);

        // Expand env vars like %VAR%
        input = expandEnvVars(input);

        // Tokenize
        String[] parts = tokenize(input);
        if (parts.length == 0) return "";

        String cmd = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        String rawArgs = input.length() > parts[0].length() ? input.substring(parts[0].length()).trim() : "";

        switch (cmd) {
            case "help":       return cmdHelp(args);
            case "cls":        return "\u001BCLS";  // special signal
            case "echo":       return cmdEcho(rawArgs);
            case "dir":        return cmdDir(args);
            case "cd":
            case "chdir":      return cmdCd(rawArgs);
            case "md":
            case "mkdir":      return cmdMkdir(rawArgs);
            case "rd":
            case "rmdir":      return cmdRmdir(args, rawArgs);
            case "del":
            case "erase":      return cmdDel(args, rawArgs);
            case "copy":       return cmdCopy(args);
            case "move":       return cmdMove(args);
            case "ren":
            case "rename":     return cmdRen(args);
            case "type":       return cmdType(rawArgs);
            case "more":       return cmdType(rawArgs);
            case "set":        return cmdSet(rawArgs);
            case "ver":        return cmdVer();
            case "date":       return cmdDate(rawArgs);
            case "time":       return cmdTime(rawArgs);
            case "pause":      return "Press any key to continue . . .";
            case "exit":       return "\u001BEXIT";
            case "color":      return cmdColor(rawArgs);
            case "title":      return "";
            case "prompt":     return "";
            case "path":       return cmdPath(rawArgs);
            case "attrib":     return cmdAttrib(args);
            case "find":       return cmdFind(args, rawArgs);
            case "findstr":    return cmdFindstr(args, rawArgs);
            case "tree":       return cmdTree(rawArgs);
            case "vol":        return cmdVol();
            case "label":      return "";
            case "format":     return "Access is denied.";
            case "diskcopy":   return "Access is denied.";
            case "xcopy":      return cmdXcopy(args);
            case "robocopy":   return cmdRobocopy(args);
            case "fc":         return cmdFc(args);
            case "comp":       return cmdComp(args);
            case "sort":       return cmdSort(args, rawArgs);
            case "if":         return cmdIf(rawArgs);
            case "for":        return "The syntax of this command is:\r\nFOR %variable IN (set) DO command\r\n";
            case "goto":       return "";
            case "call":       return "CALL: Cannot call a label in batch mode from command line.";
            case "start":      return cmdStart(rawArgs);
            case "tasklist":   return cmdTasklist(args);
            case "taskkill":   return cmdTaskkill(args);
            case "ping":
                cmdPingAsync(rawArgs, asyncCallback);
                return null; // async
            case "ipconfig":   return cmdIpconfig(args);
            case "netstat":    return cmdNetstat();
            case "net":        return cmdNet(args);
            case "nslookup":
                cmdNslookupAsync(rawArgs, asyncCallback);
                return null;
            case "tracert":
                cmdTracertAsync(rawArgs, asyncCallback);
                return null;
            case "arp":        return cmdArp();
            case "route":      return cmdRoute();
            case "hostname":   return envVars.get("COMPUTERNAME");
            case "whoami":     return envVars.get("COMPUTERNAME").toLowerCase() + "\\" + envVars.get("USERNAME").toLowerCase();
            case "systeminfo": return cmdSysteminfo();
            case "wmic":       return cmdWmic(args);
            case "reg":        return cmdReg(args);
            case "sfc":        return "Beginning system scan.  This process will take some time.\r\n\r\nBeginning verification phase of system scan.\r\nVerification 100% complete.\r\n\r\nWindows Resource Protection did not find any integrity violations.";
            case "chkdsk":     return cmdChkdsk(args);
            case "diskpart":   return "Microsoft DiskPart version 10.0.19041.964\r\n\r\nCopyright (C) Microsoft Corporation.\r\nOn computer: " + envVars.get("COMPUTERNAME");
            case "shutdown":   return cmdShutdown(args);
            case "logoff":     return "";
            case "clip":       return cmdClip(rawArgs);
            case "assoc":      return cmdAssoc(rawArgs);
            case "ftype":      return cmdFtype(rawArgs);
            case "mode":       return "Status for device CON:\r\n-----------------------\r\n    Lines:          25\r\n    Columns:        80\r\n    Keyboard rate:  31\r\n    Keyboard delay: 1\r\n    Code page:      437";
            case "chcp":       return cmdChcp(rawArgs);
            case "ver_info":   return cmdVer();
            case "popd":       return "";
            case "pushd":      return "";
            case "driverquery": return cmdDriverquery();
            case "sc":         return cmdSc(args);
            case "net1":       return cmdNet(args);
            case "cipher":     return "Listing C:\\Users\\User\\\r\n New files added to this directory will not be encrypted.\r\n\r\nU " + getWindowsPath();
            case "compact":    return " 1 files within 1 directories were compacted.\r\n 1 total files, 0 files left.";
            case "cacls":
            case "icacls":     return cmdIcacls(args);
            case "takeown":    return "SUCCESS: The file (or folder): \"" + (args.length > 1 ? args[1] : "") + "\" now owned by the administrators group.";
            case "expand":     return "Microsoft (R) File Expansion Utility  Version 10.0.19041.1\r\nCopyright (C) Microsoft Corporation. All rights reserved.";
            case "extract":    return "";
            case "where":      return cmdWhere(args);
            case "which":      return cmdWhere(args);
            case "print":      return "The system cannot find the path specified.";
            case "subst":      return "";
            case "recover":    return "The system cannot find the path specified.";
            case "replace":    return cmdReplace(args);
            case "verify":     return "VERIFY is on.";
            case "break":      return "";
            case "rem":        return "";
            case "setlocal":   return "";
            case "endlocal":   return "";
            case "defined":    return "";
            default:
                return "'" + parts[0] + "' is not recognized as an internal or external command,\r\noperable program or batch file.";
        }
    }

    // ─── ENVIRONMENT ───────────────────────────────────────────────────────────

    private String expandEnvVars(String input) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) == '%') {
                int end = input.indexOf('%', i + 1);
                if (end != -1) {
                    String varName = input.substring(i + 1, end).toUpperCase();
                    String val = envVars.getOrDefault(varName, "");
                    sb.append(val);
                    i = end + 1;
                } else {
                    sb.append(input.charAt(i++));
                }
            } else {
                sb.append(input.charAt(i++));
            }
        }
        return sb.toString();
    }

    private String[] tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (char c : input.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens.toArray(new String[0]);
    }

    // ─── COMMANDS ──────────────────────────────────────────────────────────────

    private String cmdHelp(String[] args) {
        if (args.length > 0) {
            return getCommandHelp(args[0].toLowerCase());
        }
        return "For more information on a specific command, type HELP command-name\r\n" +
               "ASSOC          Displays or modifies file extension associations.\r\n" +
               "ATTRIB         Displays or changes file attributes.\r\n" +
               "BREAK          Sets or clears extended CTRL+C checking.\r\n" +
               "CALL           Calls one batch program from another.\r\n" +
               "CD             Displays the name of or changes the current directory.\r\n" +
               "CHCP           Displays or sets the active code page number.\r\n" +
               "CHDIR          Displays the name of or changes the current directory.\r\n" +
               "CHKDSK         Checks a disk and displays a status report.\r\n" +
               "CLS            Clears the screen.\r\n" +
               "COLOR          Sets the default console foreground and background colors.\r\n" +
               "COMP           Compares the contents of two files or sets of files.\r\n" +
               "COMPACT        Displays or alters the compression of files on NTFS partitions.\r\n" +
               "COPY           Copies one or more files to another location.\r\n" +
               "DATE           Displays or sets the date.\r\n" +
               "DEL            Deletes one or more files.\r\n" +
               "DIR            Displays a list of files and subdirectories in a directory.\r\n" +
               "DISKPART       Displays or configures Disk Partition properties.\r\n" +
               "DRIVERQUERY    Displays current device driver status and properties.\r\n" +
               "ECHO           Displays messages, or turns command echoing on or off.\r\n" +
               "ENDLOCAL       Ends localization of environment changes in a batch file.\r\n" +
               "ERASE          Deletes one or more files.\r\n" +
               "EXIT           Quits the CMD.EXE program (command interpreter).\r\n" +
               "FC             Compares two files or sets of files, and displays the differences.\r\n" +
               "FIND           Searches for a text string in a file or files.\r\n" +
               "FINDSTR        Searches for strings in files.\r\n" +
               "FOR            Runs a specified command for each file in a set of files.\r\n" +
               "FORMAT         Formats a disk for use with Windows.\r\n" +
               "FTYPE          Displays or modifies file types used in file extension associations.\r\n" +
               "GOTO           Directs the Windows command interpreter to a labeled line.\r\n" +
               "HELP           Provides Help information for Windows commands.\r\n" +
               "ICACLS         Display, modify, backup, or restore ACLs for files and directories.\r\n" +
               "IF             Performs conditional processing in batch programs.\r\n" +
               "LABEL          Creates, changes, or deletes the volume label of a disk.\r\n" +
               "MD             Creates a directory.\r\n" +
               "MKDIR          Creates a directory.\r\n" +
               "MKLINK         Creates Symbolic Links and Hard Links.\r\n" +
               "MODE           Configures a system device.\r\n" +
               "MORE           Displays output one screen at a time.\r\n" +
               "MOVE           Moves one or more files from one directory to another directory.\r\n" +
               "NET            Displays, modifies, and manages network configuration.\r\n" +
               "PATH           Displays or sets a search path for executable files.\r\n" +
               "PAUSE          Suspends processing of a batch file and displays a message.\r\n" +
               "POPD           Restores the previous value of the current directory saved by PUSHD.\r\n" +
               "PRINT          Prints a text file.\r\n" +
               "PROMPT         Changes the Windows command prompt.\r\n" +
               "PUSHD          Saves the current directory then changes it.\r\n" +
               "RD             Removes a directory.\r\n" +
               "RECOVER        Recovers readable information from a bad or defective disk.\r\n" +
               "REM            Records comments (remarks) in batch files or CONFIG.SYS.\r\n" +
               "REN            Renames a file or files.\r\n" +
               "RENAME         Renames a file or files.\r\n" +
               "REPLACE        Replaces files.\r\n" +
               "RMDIR          Removes a directory.\r\n" +
               "ROBOCOPY       Advanced utility to copy files and directory trees.\r\n" +
               "SC             Service Control Manager.\r\n" +
               "SET            Displays, sets, or removes Windows environment variables.\r\n" +
               "SETLOCAL       Begins localization of environment changes in a batch file.\r\n" +
               "SFC            System File Checker.\r\n" +
               "SHUTDOWN       Allows proper local or remote shutdown of machine.\r\n" +
               "SORT           Sorts input.\r\n" +
               "START          Starts a separate window to run a specified program or command.\r\n" +
               "SUBST          Associates a path with a drive letter.\r\n" +
               "SYSTEMINFO     Displays machine specific properties and configuration.\r\n" +
               "TASKKILL       Kill or stop a running process or application.\r\n" +
               "TASKLIST       Displays all currently running tasks including services.\r\n" +
               "TIME           Displays or sets the system time.\r\n" +
               "TITLE          Sets the window title for a CMD.EXE session.\r\n" +
               "TREE           Graphically displays the directory structure of a drive or path.\r\n" +
               "TYPE           Displays the contents of a text file.\r\n" +
               "VER            Displays the Windows version.\r\n" +
               "VERIFY         Tells Windows whether to verify that your files are written correctly.\r\n" +
               "VOL            Displays a disk volume label and serial number.\r\n" +
               "XCOPY          Copies files and directory trees.\r\n" +
               "WMIC           Displays WMI information inside interactive command shell.";
    }

    private String getCommandHelp(String cmd) {
        switch(cmd) {
            case "dir": return "Displays a list of files and subdirectories in a directory.\r\n\r\nDIR [drive:][path][filename] [/A[[:]attributes]] [/B] [/C] [/D] [/L] [/N]\r\n  [/O[[:]sortorder]] [/P] [/Q] [/R] [/S] [/T[[:]timefield]] [/W] [/X] [/4]";
            case "cd":  return "Displays the name of or changes the current directory.\r\n\r\nCHDIR [/D] [drive:][path]\r\nCHDIR [..]\r\nCD [/D] [drive:][path]\r\nCD [..]";
            case "del": return "Deletes one or more files.\r\n\r\nDEL [/P] [/F] [/S] [/Q] [/A[[:]attributes]] names\r\nERASE [/P] [/F] [/S] [/Q] [/A[[:]attributes]] names";
            default:    return "Help for " + cmd.toUpperCase() + " is not available in this version.";
        }
    }

    private String cmdEcho(String rawArgs) {
        if (rawArgs.isEmpty()) return "ECHO is on.";
        if (rawArgs.equalsIgnoreCase("on")) return "";
        if (rawArgs.equalsIgnoreCase("off")) return "";
        // Echo with a dot trick: ECHO. prints blank line
        if (rawArgs.equals(".")) return "";
        return rawArgs;
    }

    private String cmdDir(String[] args) {
        File dir = currentDir;
        String pattern = "*";
        boolean showAll = false, wide = false, bare = false;

        for (String a : args) {
            if (a.startsWith("/")) {
                if (a.equalsIgnoreCase("/A") || a.equalsIgnoreCase("/A:H")) showAll = true;
                if (a.equalsIgnoreCase("/W")) wide = true;
                if (a.equalsIgnoreCase("/B")) bare = true;
            } else if (!a.contains("/")) {
                File f = resolve(a);
                if (f.isDirectory()) dir = f;
                else pattern = a;
            }
        }

        File[] files = dir.listFiles();
        if (files == null) files = new File[0];

        // Sort: dirs first, then files
        List<File> dirs = new ArrayList<>(), fs = new ArrayList<>();
        for (File f : files) {
            if (f.isDirectory()) dirs.add(f);
            else fs.add(f);
        }
        Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        Collections.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        if (bare) {
            StringBuilder sb = new StringBuilder();
            for (File f : dirs) sb.append(f.getName()).append("\r\n");
            for (File f : fs) sb.append(f.getName()).append("\r\n");
            return sb.toString().trim();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy  hh:mm aa", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append(" Volume in drive C has no label.\r\n");
        sb.append(" Volume Serial Number is A1B2-C3D4\r\n\r\n");
        sb.append(String.format(" Directory of %s\r\n\r\n", getWindowsPath()));

        long totalSize = 0;
        int fileCount = 0, dirCount = 0;

        // Parent dirs
        sb.append(String.format("%-22s %-14s %s\r\n",
                sdf.format(new Date(dir.lastModified())), "<DIR>", "."));
        sb.append(String.format("%-22s %-14s %s\r\n",
                sdf.format(new Date(dir.lastModified())), "<DIR>", ".."));
        dirCount += 2;

        for (File f : dirs) {
            sb.append(String.format("%-22s %-14s %s\r\n",
                    sdf.format(new Date(f.lastModified())), "<DIR>", f.getName()));
            dirCount++;
        }
        for (File f : fs) {
            long size = f.length();
            totalSize += size;
            fileCount++;
            sb.append(String.format("%-22s %,14d %s\r\n",
                    sdf.format(new Date(f.lastModified())), size, f.getName()));
        }

        sb.append(String.format("%16d File(s) %,16d bytes\r\n", fileCount, totalSize));
        sb.append(String.format("%16d Dir(s)  %,15d bytes free\r\n", dirCount,
                dir.getFreeSpace()));
        return sb.toString();
    }

    private String cmdCd(String rawArgs) {
        if (rawArgs.isEmpty()) {
            return getWindowsPath();
        }
        if (rawArgs.equals("..")) {
            File parent = currentDir.getParentFile();
            if (parent != null && parent.exists()) currentDir = parent;
            return "";
        }
        File target = resolve(rawArgs);
        if (target.exists() && target.isDirectory()) {
            currentDir = target;
        } else {
            return "The system cannot find the path specified.";
        }
        return "";
    }

    private String cmdMkdir(String rawArgs) {
        if (rawArgs.isEmpty()) return "The syntax of the command is incorrect.";
        File dir = resolve(rawArgs);
        if (dir.mkdirs()) return "";
        return "A subdirectory or file " + rawArgs + " already exists.";
    }

    private String cmdRmdir(String[] args, String rawArgs) {
        boolean recursive = false;
        String path = rawArgs;
        for (String a : args) {
            if (a.equalsIgnoreCase("/S") || a.equalsIgnoreCase("/Q")) recursive = true;
            else path = a;
        }
        File dir = resolve(path);
        if (!dir.exists()) return "The system cannot find the path specified.";
        if (!dir.isDirectory()) return "The directory name is invalid.";
        if (recursive) {
            deleteRecursive(dir);
            return "";
        }
        if (dir.delete()) return "";
        return "The directory is not empty.";
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    private String cmdDel(String[] args, String rawArgs) {
        boolean force = false, quiet = false;
        String path = rawArgs;
        for (String a : args) {
            if (a.equalsIgnoreCase("/F")) force = true;
            else if (a.equalsIgnoreCase("/Q")) quiet = true;
            else if (!a.startsWith("/")) path = a;
        }
        File f = resolve(path);
        if (!f.exists()) return "Could Not Find " + path;
        f.delete();
        return "";
    }

    private String cmdCopy(String[] args) {
        if (args.length < 2) return "The syntax of the command is incorrect.";
        File src = resolve(args[0]), dst = resolve(args[1]);
        if (!src.exists()) return "The system cannot find the file specified.";
        try {
            java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "        1 file(s) copied.";
        } catch (IOException e) {
            return "Access is denied.";
        }
    }

    private String cmdMove(String[] args) {
        if (args.length < 2) return "The syntax of the command is incorrect.";
        File src = resolve(args[0]), dst = resolve(args[1]);
        if (!src.exists()) return "The system cannot find the file specified.";
        if (src.renameTo(dst)) return "        1 file(s) moved.";
        return "Access is denied.";
    }

    private String cmdRen(String[] args) {
        if (args.length < 2) return "The syntax of the command is incorrect.";
        File src = resolve(args[0]);
        File dst = new File(src.getParent(), args[1]);
        if (!src.exists()) return "The system cannot find the file specified.";
        if (src.renameTo(dst)) return "";
        return "Access is denied.";
    }

    private String cmdType(String rawArgs) {
        if (rawArgs.isEmpty()) return "The syntax of the command is incorrect.";
        File f = resolve(rawArgs);
        if (!f.exists()) return "The system cannot find the file specified.";
        if (f.isDirectory()) return "Access is denied.";
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\r\n");
            br.close();
            return sb.toString();
        } catch (IOException e) {
            return "Access is denied.";
        }
    }

    private String cmdSet(String rawArgs) {
        if (rawArgs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            List<String> keys = new ArrayList<>(envVars.keySet());
            Collections.sort(keys);
            for (String k : keys) sb.append(k).append("=").append(envVars.get(k)).append("\r\n");
            return sb.toString().trim();
        }
        int eq = rawArgs.indexOf('=');
        if (eq == -1) {
            String val = envVars.get(rawArgs.toUpperCase());
            return val != null ? rawArgs.toUpperCase() + "=" + val : "Environment variable " + rawArgs + " not defined";
        }
        String key = rawArgs.substring(0, eq).toUpperCase();
        String val = rawArgs.substring(eq + 1);
        if (val.isEmpty()) envVars.remove(key);
        else envVars.put(key, val);
        return "";
    }

    private String cmdVer() {
        return "\r\nMicrosoft Windows [Version 10.0.19045.4651]\r\n(c) Microsoft Corporation. All rights reserved.";
    }

    private String cmdDate(String rawArgs) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MM/dd/yyyy", Locale.US);
        return "The current date is: " + sdf.format(new Date()) + "\r\nEnter the new date: (MM-DD-YY) ";
    }

    private String cmdTime(String rawArgs) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SS", Locale.US);
        return "The current time is: " + sdf.format(new Date()) + "\r\nEnter the new time: ";
    }

    private String cmdColor(String rawArgs) {
        // Just acknowledge; actual color change is cosmetic
        if (rawArgs.isEmpty()) return "";
        return ""; // could signal color change to activity
    }

    private String cmdPath(String rawArgs) {
        if (rawArgs.isEmpty()) {
            return "PATH=" + envVars.getOrDefault("PATH",
                    "C:\\Windows\\system32;C:\\Windows;C:\\Windows\\System32\\Wbem;C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\");
        }
        envVars.put("PATH", rawArgs);
        return "";
    }

    private String cmdAttrib(String[] args) {
        StringBuilder sb = new StringBuilder();
        File dir = currentDir;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                sb.append("                   ").append(getWindowsPath()).append("\\").append(f.getName()).append("\r\n");
            }
        }
        return sb.toString().trim();
    }

    private String cmdFind(String[] args, String rawArgs) {
        // FIND "string" file
        if (args.length < 1) return "FIND: Parameter format not correct";
        String search = args[0].replace("\"", "");
        if (args.length < 2) return "FIND: File not found - ";
        File f = resolve(args[1]);
        if (!f.exists()) return "File not found - " + args[1];
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            sb.append("\r\n---------- ").append(args[1].toUpperCase()).append("\r\n");
            String line; int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.contains(search)) sb.append(line).append("\r\n");
            }
            br.close();
            return sb.toString();
        } catch (IOException e) {
            return "Access is denied.";
        }
    }

    private String cmdFindstr(String[] args, String rawArgs) {
        return cmdFind(args, rawArgs);
    }

    private String cmdTree(String rawArgs) {
        StringBuilder sb = new StringBuilder();
        File root = rawArgs.isEmpty() ? currentDir : resolve(rawArgs);
        sb.append("Folder PATH listing for volume OS\r\n");
        sb.append("Volume serial number is A1B2-C3D4\r\n");
        sb.append(getWindowsPath()).append("\r\n");
        buildTree(root, "", sb, 0);
        return sb.toString();
    }

    private void buildTree(File dir, String prefix, StringBuilder sb, int depth) {
        if (depth > 5) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        List<File> dirs = new ArrayList<>();
        for (File f : children) if (f.isDirectory()) dirs.add(f);
        Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (int i = 0; i < dirs.size(); i++) {
            boolean last = i == dirs.size() - 1;
            sb.append(prefix).append(last ? "\u2514\u2500\u2500\u2500" : "\u251C\u2500\u2500\u2500")
              .append(dirs.get(i).getName()).append("\r\n");
            buildTree(dirs.get(i), prefix + (last ? "    " : "\u2502   "), sb, depth + 1);
        }
    }

    private String cmdVol() {
        return " Volume in drive C is OS\r\n Volume Serial Number is A1B2-C3D4";
    }

    private String cmdXcopy(String[] args) {
        if (args.length < 1) return "File not found";
        return cmdCopy(args);
    }

    private String cmdRobocopy(String[] args) {
        if (args.length < 2) return "ERROR: No Destination Directory Specified.";
        return "-------------------------------------------------------------------------------\r\n" +
               "   ROBOCOPY     ::     Robust File Copy for Windows\r\n" +
               "-------------------------------------------------------------------------------\r\n\r\n" +
               "  Started : " + new SimpleDateFormat("EEEE, MMMM dd, yyyy HH:mm:ss", Locale.US).format(new Date()) + "\r\n" +
               "   Source : " + args[0] + "\\\r\n" +
               "     Dest : " + args[1] + "\\\r\n\r\n" +
               "    Files : *.*\r\n\r\n" +
               "  Options : *.* /DCOPY:DA /COPY:DAT /R:1000000 /W:30\r\n\r\n" +
               "------------------------------------------------------------------------------\r\n\r\n" +
               "\t\t\t   0\tC:\\  \r\n\r\n" +
               "------------------------------------------------------------------------------\r\n\r\n" +
               "               Total    Copied   Skipped  Mismatch    FAILED    Extras\r\n" +
               "    Dirs :         0         0         0         0         0         0\r\n" +
               "   Files :         0         0         0         0         0         0\r\n" +
               "   Bytes :         0         0         0         0         0         0\r\n" +
               "   Times :   0:00:00   0:00:00                       0:00:00   0:00:00\r\n";
    }

    private String cmdFc(String[] args) {
        if (args.length < 2) return "FC: Syntax error";
        return "Comparing files " + args[0] + " and " + args[1] + "\r\nFC: no differences encountered";
    }

    private String cmdComp(String[] args) {
        if (args.length < 2) return "Type the name of the Compare file or drive: ";
        return "Comparing " + args[0] + " and " + args[1] + "...\r\nFiles compare OK";
    }

    private String cmdSort(String[] args, String rawArgs) {
        return ""; // Would need piping support
    }

    private String cmdIf(String rawArgs) {
        return ""; // Batch IF logic stub
    }

    private String cmdStart(String rawArgs) {
        return "";
    }

    // ─── NETWORK COMMANDS ──────────────────────────────────────────────────────

    private void cmdPingAsync(String rawArgs, OutputCallback cb) {
        if (cb == null) return;
        String[] parts = rawArgs.trim().split("\\s+");
        String host = "";
        int count = 4;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("-n") || parts[i].equalsIgnoreCase("/n")) {
                if (i + 1 < parts.length) {
                    try { count = Integer.parseInt(parts[i + 1]); i++; } catch (Exception ignored) {}
                }
            } else if (!parts[i].startsWith("-") && !parts[i].startsWith("/")) {
                host = parts[i];
            }
        }

        if (host.isEmpty()) {
            cb.onError("Bad parameter.\r\nUsage: ping [-n count] [-l size] [-t] target_name");
            cb.onDone(getPrompt());
            return;
        }

        final String finalHost = host;
        final int finalCount = count;
        new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getByName(finalHost);
                String resolvedIp = addr.getHostAddress();
                cb.onOutput("\r\nPinging " + finalHost + " [" + resolvedIp + "] with 32 bytes of data:");

                int received = 0;
                long minTime = Long.MAX_VALUE, maxTime = 0, totalTime = 0;

                for (int i = 0; i < finalCount; i++) {
                    long start = System.currentTimeMillis();
                    boolean reachable = addr.isReachable(3000);
                    long elapsed = System.currentTimeMillis() - start;

                    if (reachable) {
                        received++;
                        if (elapsed < minTime) minTime = elapsed;
                        if (elapsed > maxTime) maxTime = elapsed;
                        totalTime += elapsed;
                        cb.onOutput("Reply from " + resolvedIp + ": bytes=32 time=" + elapsed + "ms TTL=128");
                    } else {
                        // Even if isReachable returns false, many hosts still respond via raw sockets
                        // Try a socket connect as fallback
                        boolean socketOk = false;
                        try {
                            java.net.Socket s = new java.net.Socket();
                            s.connect(new java.net.InetSocketAddress(addr, 80), 2000);
                            s.close();
                            socketOk = true;
                        } catch (Exception ignored) {}

                        if (socketOk) {
                            received++;
                            elapsed = System.currentTimeMillis() - start;
                            if (elapsed < minTime) minTime = elapsed;
                            if (elapsed > maxTime) maxTime = elapsed;
                            totalTime += elapsed;
                            cb.onOutput("Reply from " + resolvedIp + ": bytes=32 time=" + elapsed + "ms TTL=128");
                        } else {
                            cb.onOutput("Request time out.");
                        }
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }

                int lost = finalCount - received;
                int lostPct = (lost * 100) / finalCount;
                cb.onOutput("\r\nPing statistics for " + resolvedIp + ":");
                cb.onOutput("    Packets: Sent = " + finalCount + ", Received = " + received +
                        ", Lost = " + lost + " (" + lostPct + "% loss),");

                if (received > 0) {
                    long avg = totalTime / received;
                    cb.onOutput("Approximate round trip times in milli-seconds:");
                    cb.onOutput("    Minimum = " + minTime + "ms, Maximum = " + maxTime + "ms, Average = " + avg + "ms");
                }
            } catch (Exception e) {
                cb.onError("Ping request could not find host " + finalHost +
                        ". Please check the name and try again.");
            }
            cb.onDone(getPrompt());
        }).start();
    }

    private void cmdNslookupAsync(String rawArgs, OutputCallback cb) {
        if (cb == null) return;
        String host = rawArgs.trim().split("\\s+")[0];
        if (host.isEmpty()) {
            cb.onOutput("Default Server:  dns.google\r\nAddress:  8.8.8.8\r\n\r\n> ");
            cb.onDone(getPrompt());
            return;
        }
        new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getByName(host);
                cb.onOutput("Server:  dns.google\r\nAddress:  8.8.8.8\r\n");
                cb.onOutput("Non-authoritative answer:");
                cb.onOutput("Name:    " + addr.getHostName());
                cb.onOutput("Address: " + addr.getHostAddress());
            } catch (Exception e) {
                cb.onError("*** dns.google can't find " + host + ": Non-existent domain");
            }
            cb.onDone(getPrompt());
        }).start();
    }

    private void cmdTracertAsync(String rawArgs, OutputCallback cb) {
        if (cb == null) return;
        String host = rawArgs.trim().split("\\s+")[0];
        if (host.isEmpty()) {
            cb.onError("Usage: tracert [-d] [-h maximum_hops] [-j host-list] [-w timeout] target_name");
            cb.onDone(getPrompt());
            return;
        }
        new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getByName(host);
                cb.onOutput("\r\nTracing route to " + host + " [" + addr.getHostAddress() + "]");
                cb.onOutput("over a maximum of 30 hops:\r\n");

                // Simulate hops (realistic-looking)
                String[] hops = {
                    "192.168.1.1", "10.0.0.1", "172.16.1.1", "41.58.0.1",
                    "196.216.2.1", addr.getHostAddress()
                };
                String[] hopNames = {
                    "_gateway", "10.0.0.1", "172.16.1.1", "41.58.0.1",
                    "196.216.2.1", host
                };

                for (int i = 0; i < hops.length; i++) {
                    long t1 = (long)(Math.random() * 20 + 5);
                    long t2 = t1 + (long)(Math.random() * 5);
                    long t3 = t2 + (long)(Math.random() * 5);
                    cb.onOutput(String.format("  %2d    %2d ms    %2d ms    %2d ms  %s [%s]",
                            i + 1, t1, t2, t3, hopNames[i], hops[i]));
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                }
                cb.onOutput("\r\nTrace complete.");
            } catch (Exception e) {
                cb.onError("Unable to resolve target system name " + host + ".");
            }
            cb.onDone(getPrompt());
        }).start();
    }

    private String cmdIpconfig(String[] args) {
        boolean all = false;
        for (String a : args) if (a.equalsIgnoreCase("/all")) all = true;

        StringBuilder sb = new StringBuilder();

        if (all) {
            sb.append("\r\nWindows IP Configuration\r\n\r\n");
            sb.append("   Host Name . . . . . . . . . . . : ").append(envVars.get("COMPUTERNAME")).append("\r\n");
            sb.append("   Primary Dns Suffix  . . . . . . : \r\n");
            sb.append("   Node Type . . . . . . . . . . . : Hybrid\r\n");
            sb.append("   IP Routing Enabled. . . . . . . : No\r\n");
            sb.append("   WINS Proxy Enabled. . . . . . . : No\r\n\r\n");
        } else {
            sb.append("\r\nWindows IP Configuration\r\n\r\n");
        }

        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics != null) {
                while (nics.hasMoreElements()) {
                    NetworkInterface nic = nics.nextElement();
                    if (!nic.isUp() || nic.isLoopback()) continue;

                    String nicName = nic.getDisplayName();
                    String adapterType = nicName.toLowerCase().contains("wifi") || nicName.toLowerCase().contains("wlan")
                            ? "Wireless LAN adapter Wi-Fi" : "Ethernet adapter Ethernet";

                    sb.append(adapterType).append(":\r\n\r\n");

                    if (all) {
                        sb.append("   Connection-specific DNS Suffix  : \r\n");
                        sb.append("   Description . . . . . . . . . . : ").append(nicName).append("\r\n");
                        try {
                            byte[] mac = nic.getHardwareAddress();
                            if (mac != null) {
                                StringBuilder macSb = new StringBuilder();
                                for (int i = 0; i < mac.length; i++) {
                                    if (i > 0) macSb.append("-");
                                    macSb.append(String.format("%02X", mac[i]));
                                }
                                sb.append("   Physical Address. . . . . . . . : ").append(macSb).append("\r\n");
                            }
                        } catch (Exception ignored) {}
                        sb.append("   DHCP Enabled. . . . . . . . . . : Yes\r\n");
                        sb.append("   Autoconfiguration Enabled . . . : Yes\r\n");
                    }

                    Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        java.net.InetAddress addr = addrs.nextElement();
                        if (addr instanceof java.net.Inet4Address) {
                            sb.append("   IPv4 Address. . . . . . . . . . : ").append(addr.getHostAddress()).append("\r\n");
                            sb.append("   Subnet Mask . . . . . . . . . . : 255.255.255.0\r\n");
                            sb.append("   Default Gateway . . . . . . . . : ").append(getGateway(addr.getHostAddress())).append("\r\n");
                            if (all) {
                                sb.append("   DHCP Server . . . . . . . . . . : ").append(getGateway(addr.getHostAddress())).append("\r\n");
                                sb.append("   DNS Servers . . . . . . . . . . : 8.8.8.8\r\n");
                                sb.append("                                       8.8.4.4\r\n");
                            }
                        } else if (addr instanceof java.net.Inet6Address && all) {
                            sb.append("   Link-local IPv6 Address . . . . : ").append(addr.getHostAddress()).append("%").append(nic.getIndex()).append("(Preferred)\r\n");
                        }
                    }
                    sb.append("\r\n");
                }
            }
        } catch (Exception e) {
            sb.append("   Media State . . . . . . . . . . : Media disconnected\r\n\r\n");
        }

        return sb.toString();
    }

    private String getGateway(String ip) {
        // Derive gateway from IP (x.x.x.1)
        try {
            String[] parts = ip.split("\\.");
            return parts[0] + "." + parts[1] + "." + parts[2] + ".1";
        } catch (Exception e) {
            return "192.168.1.1";
        }
    }

    private String cmdNetstat() {
        return "\r\nActive Connections\r\n\r\n" +
               "  Proto  Local Address          Foreign Address        State\r\n" +
               "  TCP    0.0.0.0:135            0.0.0.0:0              LISTENING\r\n" +
               "  TCP    0.0.0.0:445            0.0.0.0:0              LISTENING\r\n" +
               "  TCP    0.0.0.0:5040           0.0.0.0:0              LISTENING\r\n" +
               "  TCP    127.0.0.1:63342        0.0.0.0:0              LISTENING\r\n" +
               "  TCP    127.0.0.1:65001        0.0.0.0:0              LISTENING\r\n";
    }

    private String cmdNet(String[] args) {
        if (args.length == 0) return "The syntax of this command is:\r\nNET [ ACCOUNTS | COMPUTER | CONFIG | CONTINUE | FILE | GROUP | HELP |\r\n      HELPMSG | LOCALGROUP | PAUSE | PRINT | SESSION | SHARE | START |\r\n      STATISTICS | STOP | TIME | USE | USER | VIEW ]";
        switch (args[0].toLowerCase()) {
            case "user": return cmdNetUser();
            case "start": return "These Windows services are started:\r\n\r\n   Application Information\r\n   Background Intelligent Transfer Service\r\n   Base Filtering Engine\r\n   Windows Event Log\r\nThe command completed successfully.";
            case "stop":  return "The service is stopping.\r\nThe service was stopped successfully.";
            default: return "This command is not supported by the help utility.";
        }
    }

    private String cmdNetUser() {
        return "\r\nUser accounts for \\\\" + envVars.get("COMPUTERNAME") + "\r\n\r\n" +
               "-------------------------------------------------------------------------------\r\n" +
               "Administrator            DefaultAccount           Guest\r\n" +
               "User                     WDAGUtilityAccount\r\n" +
               "The command completed successfully.";
    }

    private String cmdArp() {
        return "\r\nInterface: 192.168.1.100 --- 0x4\r\n" +
               "  Internet Address      Physical Address      Type\r\n" +
               "  192.168.1.1           aa-bb-cc-dd-ee-ff     dynamic\r\n" +
               "  192.168.1.255         ff-ff-ff-ff-ff-ff     static\r\n" +
               "  224.0.0.22            01-00-5e-00-00-16     static\r\n" +
               "  255.255.255.255       ff-ff-ff-ff-ff-ff     static";
    }

    private String cmdRoute() {
        return "\r\n===========================================================================\r\n" +
               "Interface List\r\n" +
               " 12...aa bb cc dd ee ff ......Realtek PCIe GbE Family Controller\r\n" +
               "  1...........................Software Loopback Interface 1\r\n\r\n" +
               "===========================================================================\r\n\r\n" +
               "IPv4 Route Table\r\n" +
               "===========================================================================\r\n" +
               "Active Routes:\r\n" +
               "Network Destination        Netmask          Gateway       Interface  Metric\r\n" +
               "          0.0.0.0          0.0.0.0      192.168.1.1    192.168.1.100     35\r\n" +
               "        127.0.0.0        255.0.0.0         On-link         127.0.0.1    331\r\n" +
               "===========================================================================";
    }

    // ─── PROCESS COMMANDS ──────────────────────────────────────────────────────

    private String cmdTasklist(String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append("Image Name                     PID Session Name        Session#    Mem Usage\r\n");
        sb.append("========================= ======== ================ =========== ============\r\n");

        try {
            File procDir = new File("/proc");
            File[] pids = procDir.listFiles(f -> f.isDirectory() && f.getName().matches("\\d+"));
            if (pids != null) {
                Arrays.sort(pids, (a, b) -> Integer.parseInt(a.getName()) - Integer.parseInt(b.getName()));
                int count = 0;
                for (File pidDir : pids) {
                    if (count > 60) break;
                    try {
                        int pid = Integer.parseInt(pidDir.getName());
                        String name = readProcComm(pid);
                        if (name == null || name.isEmpty()) continue;
                        // Format exe name: max 25 chars, .exe suffix
                        String exeName = name.length() > 21 ? name.substring(0, 21) : name;
                        exeName += ".exe";
                        long memKb = readProcMemKb(pid);
                        sb.append(String.format("%-25s %8d %-16s %11d %,9d K\r\n",
                                exeName, pid, "Services", 0, Math.max(memKb, 1024)));
                        count++;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            // Fallback: show some realistic-looking processes
            sb.append(fakeProcess("System Idle Process", 0, 8));
            sb.append(fakeProcess("System", 4, 148));
            sb.append(fakeProcess("smss.exe", 348, 1064));
            sb.append(fakeProcess("csrss.exe", 512, 4328));
            sb.append(fakeProcess("wininit.exe", 600, 5912));
            sb.append(fakeProcess("services.exe", 680, 8104));
            sb.append(fakeProcess("lsass.exe", 700, 14328));
            sb.append(fakeProcess("svchost.exe", 824, 21480));
            sb.append(fakeProcess("explorer.exe", 1960, 94520));
        }
        return sb.toString();
    }

    private String fakeProcess(String name, int pid, long memKb) {
        return String.format("%-25s %8d %-16s %11d %,9d K\r\n",
                name, pid, "Services", 0, memKb);
    }

    private String readProcComm(int pid) {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/" + pid + "/comm"));
            String name = br.readLine();
            br.close();
            return name != null ? name.trim() : "";
        } catch (Exception e) {
            return null;
        }
    }

    private long readProcMemKb(int pid) {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.trim().split("\\s+");
                    br.close();
                    return Long.parseLong(parts[1]);
                }
            }
            br.close();
        } catch (Exception ignored) {}
        return 4096;
    }

    private String cmdTaskkill(String[] args) {
        String target = null;
        boolean byPid = false, byName = false, force = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("/PID") && i + 1 < args.length) {
                target = args[i + 1]; byPid = true; i++;
            } else if (args[i].equalsIgnoreCase("/IM") && i + 1 < args.length) {
                target = args[i + 1]; byName = true; i++;
            } else if (args[i].equalsIgnoreCase("/F")) {
                force = true;
            }
        }

        if (target == null) {
            return "ERROR: Invalid syntax.\r\nType \"TASKKILL /?\" for usage.";
        }

        if (byPid) {
            try {
                int pid = Integer.parseInt(target);
                // Try to kill by sending signal via Android API
                boolean killed = false;
                try {
                    android.os.Process.killProcess(pid);
                    killed = true;
                } catch (Exception e) {
                    // May require root; try shell kill
                    try {
                        Runtime.getRuntime().exec("kill -9 " + pid);
                        killed = true;
                    } catch (Exception ex) {
                        killed = false;
                    }
                }
                if (killed) return "SUCCESS: The process with PID " + pid + " has been terminated.";
                return "ERROR: The process \"" + pid + "\" not found.";
            } catch (NumberFormatException e) {
                return "ERROR: Invalid PID \"" + target + "\".";
            }
        }

        if (byName) {
            // Kill by name - search /proc for matching comm
            String name = target.replace(".exe", "");
            boolean found = false;
            File procDir = new File("/proc");
            File[] pids = procDir.listFiles(f -> f.isDirectory() && f.getName().matches("\\d+"));
            if (pids != null) {
                for (File pidDir : pids) {
                    try {
                        int pid = Integer.parseInt(pidDir.getName());
                        String comm = readProcComm(pid);
                        if (comm != null && comm.equalsIgnoreCase(name)) {
                            try {
                                android.os.Process.killProcess(pid);
                                found = true;
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (found) return "SUCCESS: The process \"" + target + "\" with PID has been terminated.";
            return "ERROR: The process \"" + target + "\" not found.";
        }

        return "ERROR: Invalid arguments. Use /PID <pid> or /IM <imagename>";
    }

    // ─── SYSTEM INFO ───────────────────────────────────────────────────────────

    private String cmdSysteminfo() {
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory() / 1024;
        long freeMem = rt.freeMemory() / 1024;

        return "\r\nHost Name:                 " + envVars.get("COMPUTERNAME") + "\r\n" +
               "OS Name:                   Microsoft Windows 10 Pro\r\n" +
               "OS Version:                10.0.19045 N/A Build 19045\r\n" +
               "OS Manufacturer:           Microsoft Corporation\r\n" +
               "OS Configuration:          Standalone Workstation\r\n" +
               "OS Build Type:             Multiprocessor Free\r\n" +
               "Registered Owner:          " + envVars.get("USERNAME") + "\r\n" +
               "Registered Organization:   N/A\r\n" +
               "Product ID:                00330-80000-00000-AA447\r\n" +
               "Original Install Date:     01/01/2024, 10:00:00 AM\r\n" +
               "System Boot Time:          " + new SimpleDateFormat("MM/dd/yyyy, hh:mm:ss aa", Locale.US).format(new Date(System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime())) + "\r\n" +
               "System Manufacturer:       " + Build.MANUFACTURER + "\r\n" +
               "System Model:              " + Build.MODEL + "\r\n" +
               "System Type:               x64-based PC\r\n" +
               "Processor(s):              " + rt.availableProcessors() + " Processor(s) Installed.\r\n" +
               "                           [01]: Intel64 Family 6 Model 94 Stepping 3 GenuineIntel ~2300 Mhz\r\n" +
               "BIOS Version:              LENOVO " + Build.ID + "\r\n" +
               "Windows Directory:         C:\\Windows\r\n" +
               "System Directory:          C:\\Windows\\system32\r\n" +
               "Boot Device:               \\Device\\HarddiskVolume1\r\n" +
               "System Locale:             en-us;English (United States)\r\n" +
               "Input Locale:              en-us;English (United States)\r\n" +
               "Time Zone:                 (UTC+00:00) Coordinated Universal Time\r\n" +
               "Total Physical Memory:     8,192 MB\r\n" +
               "Available Physical Memory: " + String.format("%,d", freeMem / 1024) + " MB\r\n" +
               "Virtual Memory: Max Size:  16,384 MB\r\n" +
               "Virtual Memory: Available: 12,288 MB\r\n" +
               "Virtual Memory: In Use:    4,096 MB\r\n" +
               "Page File Location(s):     C:\\pagefile.sys\r\n" +
               "Domain:                    WORKGROUP\r\n" +
               "Logon Server:              \\\\" + envVars.get("COMPUTERNAME") + "\r\n" +
               "Hotfix(s):                 5 Hotfix(s) Installed.\r\n" +
               "                           [01]: KB5032278\r\n" +
               "                           [02]: KB5031445\r\n" +
               "                           [03]: KB5028851\r\n" +
               "                           [04]: KB5025221\r\n" +
               "                           [05]: KB5019959\r\n" +
               "Network Card(s):           1 NIC(s) Installed.\r\n" +
               "                           [01]: Realtek PCIe GbE Family Controller\r\n" +
               "                                 Connection Name: Ethernet\r\n" +
               "                                 DHCP Enabled:    Yes\r\n" +
               "                                 DHCP Server:     192.168.1.1\r\n" +
               "                                 IP address(es)\r\n" +
               "                                 [01]: 192.168.1.100\r\n" +
               "Hyper-V Requirements:      VM Monitor Mode Extensions: Yes\r\n" +
               "                           Virtualization Enabled In Firmware: Yes\r\n" +
               "                           Second Level Address Translation: Yes\r\n" +
               "                           Data Execution Prevention Available: Yes";
    }

    private String cmdWmic(String[] args) {
        if (args.length == 0) return "wmic:root\\cli>";
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "cpu": return "Caption                                 DeviceID  Manufacturer  MaxClockSpeed  Name\r\nIntel64 Family 6 Model 94 Stepping 3  CPU0      GenuineIntel  2300           Intel(R) Core(TM) i7-6700HQ CPU @ 2.60GHz";
            case "os":  return "Caption                    FreePhysicalMemory  Name                                                         OSArchitecture  Version\r\nMicrosoft Windows 10 Pro  " + (Runtime.getRuntime().freeMemory() / 1024) + "            Microsoft Windows 10 Pro|C:\\Windows|\\Device\\Harddisk0\\Partition3  64-bit          10.0.19045";
            case "memorychip": return "BankLabel  Capacity    DeviceLocator  MemoryType  Speed  Tag\r\nBank 0     4294967296  DIMM 0         0           2400   Physical Memory 0\r\nBank 1     4294967296  DIMM 1         0           2400   Physical Memory 1";
            case "diskdrive": return "Caption                  DeviceID            Model                    Size\r\nSamsung SSD 860 EVO      \\\\.\\PHYSICALDRIVE0  Samsung SSD 860 EVO      500105249280";
            case "process": return cmdTasklist(new String[0]);
            default: return "No Instance(s) Available.";
        }
    }

    private String cmdReg(String[] args) {
        if (args.length == 0) return "REG Operation [Parameter List]\r\n\r\n  Operation  [ QUERY   | ADD    | DELETE  | COPY   |\r\n               SAVE    | LOAD   | UNLOAD  | RESTORE|\r\n               COMPARE | EXPORT | IMPORT  | FLAGS  ]";
        if (args[0].equalsIgnoreCase("query")) {
            if (args.length < 2) return "ERROR: Invalid syntax.";
            return "! REG.EXE VERSION 10.0.19041\r\n\r\nHKEY_LOCAL_MACHINE\r\n    (Default)    REG_SZ    (value not set)";
        }
        return "The operation completed successfully.";
    }

    private String cmdChkdsk(String[] args) {
        String drive = args.length > 0 ? args[0] : "C:";
        return "The type of the file system is NTFS.\r\nVolume label is OS.\r\n\r\nWARNING!  /F parameter not specified.\r\nRunning CHKDSK in read-only mode.\r\n\r\nStage 1: Examining basic file system structure ...\r\n  262144 file records processed.\r\n\r\nFile verification completed.\r\n  0 large file records processed.\r\n  0 bad file records processed.\r\n\r\nStage 2: Examining file name linkage ...\r\n  340016 index entries processed.\r\n\r\nIndex verification completed.\r\n  0 unindexed files scanned.\r\n  0 unindexed files recovered to lost and found.\r\n\r\nStage 3: Examining security descriptors ...\r\nSecurity descriptor verification completed.\r\n  30567 data files processed.\r\nCHKDSK is verifying Usn Journal...\r\n  37621384 USN bytes processed.\r\n\r\nUSN Journal verification completed.\r\n\r\nWindows has scanned the file system and found no problems.\r\nNo further action is required.\r\n\r\n 499,564,544 KB total disk space.\r\n 233,849,216 KB in use.\r\n     196,608 KB in paging file.\r\n   1,118,208 KB in bad sectors.\r\n 264,400,512 KB available on disk.\r\n\r\n      4,096 bytes in each allocation unit.\r\n124,891,136 total allocation units on disk.\r\n 66,100,128 allocation units available on disk.";
    }

    private String cmdShutdown(String[] args) {
        boolean restart = false;
        for (String a : args) if (a.equalsIgnoreCase("/r")) restart = true;
        return restart
                ? "Restarting... (This is a CMD emulator — Android device will not restart)"
                : "Shutting down... (This is a CMD emulator — Android device will not shut down)";
    }

    private String cmdClip(String rawArgs) {
        android.content.ClipboardManager cb =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) cb.setPrimaryClip(android.content.ClipData.newPlainText("cmd", rawArgs));
        return "";
    }

    private String cmdAssoc(String rawArgs) {
        if (rawArgs.isEmpty()) {
            return ".bat=batfile\r\n.cmd=cmdfile\r\n.com=comfile\r\n.exe=exefile\r\n.txt=txtfile\r\n.zip=CompressedFolder\r\n.pdf=AcroExch.Document.DC\r\n.doc=Word.Document.8\r\n.docx=Word.Document.12\r\n.xls=Excel.Sheet.8\r\n.xlsx=Excel.Sheet.12\r\n.jpg=jpegfile\r\n.png=pngfile\r\n.mp4=WMP11.AssocFile.MP4\r\n.mp3=WMP11.AssocFile.MP3";
        }
        return rawArgs + "=exefile";
    }

    private String cmdFtype(String rawArgs) {
        if (rawArgs.isEmpty()) return "batfile=C:\\Windows\\System32\\cmd.exe /c \"%1\" %*\r\nexefile=\"%1\" %*\r\ntxtfile=%SystemRoot%\\system32\\NOTEPAD.EXE %1";
        return rawArgs + "=\"%1\" %*";
    }

    private String cmdChcp(String rawArgs) {
        if (rawArgs.isEmpty()) return "Active code page: 437";
        return "Active code page: " + rawArgs;
    }

    private String cmdDriverquery() {
        return "\r\nModule Name  Display Name           Driver Type   Link Date\r\n" +
               "============ ====================== ============= =====================\r\n" +
               "1394ohci     1394 OHCI Compliant Ho Kernel        12/7/2019 5:31:00 AM\r\n" +
               "3ware        3ware                  Kernel        2/28/2016 5:07:00 AM\r\n" +
               "ACPI         Microsoft ACPI Driver  Kernel        12/7/2019 5:26:00 AM\r\n" +
               "acpiex       Microsoft ACPIEx Drive Kernel        12/7/2019 5:26:00 AM\r\n" +
               "acpipagr     ACPI Processor Aggrega Kernel        12/7/2019 5:31:00 AM\r\n" +
               "AcpiPmi      ACPI Power Meter Drive Kernel        12/7/2019 5:31:00 AM\r\n" +
               "acpitime     ACPI Wake Alarm Driver Kernel        12/7/2019 5:31:00 AM\r\n" +
               "Acx01000     Acx01000               Kernel        N/A\r\n" +
               "ADP80XX      ADP80XX                Kernel        4/9/2015 12:49:00 PM\r\n";
    }

    private String cmdSc(String[] args) {
        if (args.length < 2) return "DESCRIPTION:\r\n        SC is a command line program used for communicating with the\r\n        Service Control Manager and services.\r\nUSAGE:\r\n        sc <server> [command] [service name] <option1> <option2>...";
        return "[SC] OpenService FAILED 1060:\r\n\r\nThe specified service does not exist as an installed service.";
    }

    private String cmdIcacls(String[] args) {
        if (args.length == 0) return "ERROR: No valid arguments were passed.";
        return args[0] + " NT AUTHORITY\\SYSTEM:(I)(F)\r\n" +
               "         BUILTIN\\Administrators:(I)(F)\r\n" +
               "         BUILTIN\\Users:(I)(RX)\r\n\r\nSuccessfully processed 1 files; Failed processing 0 files";
    }

    private String cmdWhere(String[] args) {
        if (args.length == 0) return "ERROR: Missing pattern.";
        String name = args[0].replace(".exe", "");
        String[] winCmds = {"cmd", "powershell", "notepad", "calc", "mspaint", "explorer", "taskmgr", "regedit", "mmc"};
        for (String c : winCmds) {
            if (c.equalsIgnoreCase(name)) {
                return "C:\\Windows\\System32\\" + name + ".exe";
            }
        }
        return "INFO: Could not find files for the given pattern(s).";
    }

    private String cmdReplace(String[] args) {
        if (args.length < 2) return "No source files were found.";
        return "1 file(s) replaced.";
    }

    // ─── UTILITY ───────────────────────────────────────────────────────────────

    private File resolve(String path) {
        if (path == null || path.isEmpty()) return currentDir;
        // Strip surrounding quotes
        path = path.replace("\"", "").trim();
        // Ignore Windows drive letters
        if (path.length() >= 2 && path.charAt(1) == ':') path = path.substring(2);
        // Convert backslashes
        path = path.replace('\\', '/');
        if (path.startsWith("/")) return new File(path);
        return new File(currentDir, path);
    }

    public List<String> getCommandHistory() { return commandHistory; }
}
