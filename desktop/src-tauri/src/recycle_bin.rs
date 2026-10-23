use std::path::Path;

#[cfg(windows)]
pub fn recycle_file(path: &Path) -> Result<(), String> {
    let target = path.to_path_buf();
    std::thread::spawn(move || recycle_file_on_sta(&target))
        .join()
        .map_err(|_| "回收站操作异常，照片状态未确认，请检查原文件".to_owned())?
}

#[cfg(windows)]
fn recycle_file_on_sta(path: &Path) -> Result<(), String> {
    use std::os::windows::ffi::OsStrExt;
    use windows::Win32::System::Com::{
        CLSCTX_INPROC_SERVER, COINIT_APARTMENTTHREADED, CoCreateInstance, CoInitializeEx,
        CoUninitialize, IBindCtx,
    };
    use windows::Win32::UI::Shell::{
        FILEOPERATION_FLAGS, FOF_NOCONFIRMATION, FOF_NOERRORUI, FOF_SILENT, FOFX_RECYCLEONDELETE,
        FileOperation, IFileOperation, IFileOperationProgressSink, IShellItem,
        SHCreateItemFromParsingName,
    };
    use windows::core::PCWSTR;

    struct ComGuard;
    impl Drop for ComGuard {
        fn drop(&mut self) {
            unsafe { CoUninitialize() };
        }
    }

    unsafe { CoInitializeEx(None, COINIT_APARTMENTTHREADED) }
        .ok()
        .map_err(|error| format!("无法初始化 Windows 回收站：{error}"))?;
    let _com_guard = ComGuard;
    let target: Vec<u16> = path.as_os_str().encode_wide().chain(Some(0)).collect();
    let item: IShellItem =
        unsafe { SHCreateItemFromParsingName(PCWSTR(target.as_ptr()), None::<&IBindCtx>) }
            .map_err(|error| format!("无法识别文件或文件夹：{error}"))?;
    let operation: IFileOperation =
        unsafe { CoCreateInstance(&FileOperation, None, CLSCTX_INPROC_SERVER) }
            .map_err(|error| format!("无法打开 Windows 回收站：{error}"))?;
    let flags = FOFX_RECYCLEONDELETE.0 | FOF_NOCONFIRMATION.0 | FOF_NOERRORUI.0 | FOF_SILENT.0;
    unsafe { operation.SetOperationFlags(FILEOPERATION_FLAGS(flags)) }
        .map_err(|error| format!("无法设置回收站操作：{error}"))?;
    unsafe { operation.DeleteItem(&item, None::<&IFileOperationProgressSink>) }
        .map_err(|error| format!("无法提交回收站操作：{error}"))?;
    unsafe { operation.PerformOperations() }.map_err(|error| format!("无法移入回收站：{error}"))?;
    if unsafe { operation.GetAnyOperationsAborted() }
        .map_err(|error| format!("无法确认回收站操作：{error}"))?
        .as_bool()
    {
        return Err("回收站操作已取消，原文件未从索引移除".to_owned());
    }
    if path.exists() {
        return Err("文件或文件夹仍在原位置，回收站操作未完成".to_owned());
    }
    Ok(())
}

#[cfg(not(windows))]
pub fn recycle_file(_path: &Path) -> Result<(), String> {
    Err("当前仅支持 Windows 回收站".to_owned())
}
