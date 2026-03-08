@echo off
setlocal
set "APP_EXE=%~dp0LanTransferPC.exe"

if not exist "%APP_EXE%" (
  set "APP_EXE=%~dp0dist\LanTransferPC-OneFile.exe"
  if not exist "%APP_EXE%" (
    echo Missing executable: "%APP_EXE%"
    echo Build it first with PyInstaller or run the provided build command.
    exit /b 1
  )
)

start "" "%APP_EXE%"
exit /b 0
