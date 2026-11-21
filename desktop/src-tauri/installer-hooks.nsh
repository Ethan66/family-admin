; Renaming the product changes NSIS registry keys. Reuse a verified legacy
; installation only when the Chinese-name installation does not already exist.
!macro NSIS_HOOK_PREINSTALL
  ReadRegStr $R8 HKCU "Software\ethan\家庭管理" ""
  ${If} $R8 == ""
    ReadRegStr $R8 HKCU "Software\ethan\Family Admin" ""
    ReadRegStr $R9 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Family Admin" "MainBinaryName"
    ${If} $R8 != ""
    ${AndIf} $R9 == "${MAINBINARYNAME}.exe"
      IfFileExists "$R8\${MAINBINARYNAME}.exe" 0 family_admin_legacy_dir_done
      StrCpy $INSTDIR $R8
    ${EndIf}
  ${EndIf}
  family_admin_legacy_dir_done:
!macroend

; Delete only shortcuts targeting the installed binary and legacy registry keys
; that point to that same directory. User data and media are never touched.
!macro NSIS_HOOK_POSTINSTALL
  !insertmacro IsShortcutTarget "$DESKTOP\Family Admin.lnk" "$INSTDIR\${MAINBINARYNAME}.exe"
  Pop $R8
  ${If} $R8 == 1
    Delete "$DESKTOP\Family Admin.lnk"
  ${EndIf}
  !insertmacro IsShortcutTarget "$SMPROGRAMS\Family Admin.lnk" "$INSTDIR\${MAINBINARYNAME}.exe"
  Pop $R8
  ${If} $R8 == 1
    Delete "$SMPROGRAMS\Family Admin.lnk"
  ${EndIf}
  ReadRegStr $R8 HKCU "Software\ethan\Family Admin" ""
  ReadRegStr $R9 HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Family Admin" "MainBinaryName"
  ${If} $R8 == $INSTDIR
  ${AndIf} $R9 == "${MAINBINARYNAME}.exe"
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Family Admin"
    DeleteRegKey HKCU "Software\ethan\Family Admin"
  ${EndIf}
!macroend
