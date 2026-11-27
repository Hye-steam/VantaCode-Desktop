; VantaGate 桌面版 Windows 安装器（Inno Setup 6）
; 简体中文向导；产物：VantaGate-<版本>-win64.exe
; 由 Gradle 任务 :desktop:packageExeZh 调用（ISCC.exe）。
; 版本号不要手改：由 /DAppVersion 从 build.gradle.kts 传入。
; 替代 Compose 的 packageExe（jpackage + WiX）：jpackage 不向 Exe 步骤传
; --resource-dir，WiX 向导语言改不成中文；Inno Setup 自带简体中文向导。
; 安装位置与原来一致：每用户安装到 %LOCALAPPDATA%\Programs\VantaGate。

#define MyAppName "VantaGate"
#define MyAppPublisher "VantaGate"

[Setup]
AppName={#MyAppName}
AppVersion={#AppVersion}
AppPublisher={#MyAppPublisher}
AppId={{107c79cb-4652-471c-9f84-4fdae0e08743}
AppCopyright=GPL-3.0
AppComments=VantaGate - AI gateway for desktop
DefaultDirName={localappdata}\Programs\VantaGate
DefaultGroupName=VantaGate
PrivilegesRequired=lowest
OutputDir=..\build\compose\binaries\main\exe
OutputBaseFilename=VantaGate-{#AppVersion}-win64
SetupIconFile=..\build\generated\assets\icon.ico
LicenseFile=..\..\LICENSE
WizardStyle=modern
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayIcon={app}\VantaGate.exe
DisableProgramGroupPage=yes

[Languages]
; 中文语言文件随仓库自带（desktop/installer/languages/ChineseSimplified.isl，
; 取自 Inno Setup 官方仓库 jrsoftware/issrc），不依赖 runner 上 Inno Setup
; 安装目录里有没有这个文件（choco 装的就没有）。
Name: "chinesesimplified"; MessagesFile: "languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\build\compose\binaries\main\app\VantaGate\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\VantaGate.exe"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\VantaGate.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\VantaGate.exe"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent
