; 轻铃 · NSIS 安装/卸载钩子
;
; 编码要求：本文件必须保存为 **UTF-8 with BOM**。
;   makensis 靠 BOM 判断源码编码，没有 BOM 的中文会变成乱码；
;   以前的版本为了躲开这个坑全用 ASCII，现在卸载提示需要中文，只能显式带 BOM。
;
; 这里做两件事：
;   1) 清掉 v1.0.0 遗留的开机启动项
;      HKCU\Software\Microsoft\Windows\CurrentVersion\Run\com.qingtx.qingtiixing
;      （Electron 的 setLoginItemSettings 用 AppUserModelId 做值名，卸载程序并不知道）
;   2) 卸载时问一句要不要连提醒数据一起删，默认「否」

!include "LogicLib.nsh"

!macro customUnInstall
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "com.qingtx.qingtiixing"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "qing-ti-xing"

  ; 静默卸载（/S）不弹窗，也不删数据，避免批处理脚本误删
  ${ifNot} ${Silent}
    MessageBox MB_YESNO|MB_ICONEXCLAMATION \
      "是否一并删除提醒数据？$\r$\n$\r$\n是：删除所有提醒、设置和历史备份，删除后无法恢复。$\r$\n否：保留数据，以后重新安装还能接着用。$\r$\n$\r$\n（目录：$APPDATA\轻铃）" \
      /SD IDNO IDNO qtxKeepData

      RMDir /r "$APPDATA\轻铃"
      RMDir /r "$LOCALAPPDATA\轻铃"
    qtxKeepData:
  ${endif}
!macroend
