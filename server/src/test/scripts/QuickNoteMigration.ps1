$ErrorActionPreference = 'Stop'
$tempServerRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$tempWorkspace = Join-Path $tempServerRoot ('target/quick-note-db-' + [guid]::NewGuid().ToString('N'))
$tempData = Join-Path $tempWorkspace 'data'
$tempMysqld = (Get-Command mysqld).Source
$tempMysql = (Get-Command mysql).Source
$tempMysqlAdmin = Join-Path (Split-Path $tempMysql) 'mysqladmin.exe'
$tempListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$tempListener.Start()
$tempPort = $tempListener.LocalEndpoint.Port
$tempListener.Stop()
New-Item -ItemType Directory -Path $tempWorkspace | Out-Null

# 单独初始化一套数据目录，绝不复用用户的 MySQL 配置和业务数据库。
& $tempMysqld --no-defaults --initialize-insecure "--datadir=$tempData" "--log-error=$(Join-Path $tempWorkspace 'initialize.log')"
if ($LASTEXITCODE -ne 0) { throw '临时数据库初始化失败' }
$tempProcess = $null
try {
  $tempArguments = @('--no-defaults', ('--datadir="' + $tempData + '"'), '--bind-address=127.0.0.1', "--port=$tempPort", '--mysqlx=OFF', '--skip-log-bin', ('--log-error="' + (Join-Path $tempWorkspace 'mysql.log') + '"'))
  $tempProcess = Start-Process -FilePath $tempMysqld -ArgumentList $tempArguments -PassThru -WindowStyle Hidden
  $tempConnection = @('--no-defaults', '--protocol=TCP', '--host=127.0.0.1', "--port=$tempPort", '--user=root')
  $tempReady = $false
  for ($tempAttempt = 0; $tempAttempt -lt 40; $tempAttempt++) {
    & $tempMysqlAdmin @tempConnection ping 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $tempReady = $true; break }
    Start-Sleep -Milliseconds 500
  }
  if (!$tempReady) { throw '临时数据库启动超时' }
  'CREATE DATABASE quick_note_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' | & $tempMysql @tempConnection
  if ($LASTEXITCODE -ne 0) { throw '创建临时库失败' }
  $tempMigrations = Get-ChildItem -LiteralPath (Join-Path $tempServerRoot 'src/main/resources/db/migration') -Filter '*.sql' | Sort-Object { [int]($_.Name -replace '^V(\d+)__.*$', '$1') }
  foreach ($tempMigration in $tempMigrations) {
    Get-Content -LiteralPath $tempMigration.FullName -Raw -Encoding UTF8 | & $tempMysql @tempConnection --default-character-set=utf8mb4 quick_note_test
    if ($LASTEXITCODE -ne 0) { throw ('迁移失败：' + $tempMigration.Name) }
  }
  $tempSql = @'
INSERT INTO family(name,status,remark,created_by,updated_by,deleted) VALUES ('migration-test','ACTIVE','',1,1,0);
SET @family = LAST_INSERT_ID();
INSERT INTO quick_note(family_id,title,body,tags_json,image_ids_json,tasks_json,color,pinned,status,version,created_by,updated_by)
VALUES (@family,'test','body','["life"]','[]','[{"text":"todo","done":false}]','plain',false,'ACTIVE',0,1,1);
SET @note = LAST_INSERT_ID();
UPDATE quick_note SET body='saved',version=1 WHERE id=@note AND family_id=@family AND version=0 AND deleted=0;
SELECT IF(ROW_COUNT()=1,'UPDATE_OK','FAIL');
UPDATE quick_note SET body='stale',version=1 WHERE id=@note AND family_id=@family AND version=0 AND deleted=0;
SELECT IF(ROW_COUNT()=0,'CONFLICT_OK','FAIL');
SELECT IF(body='saved' AND JSON_EXTRACT(tasks_json,'$[0].done')=false AND JSON_LENGTH(tags_json)=1,'JSON_OK','FAIL') FROM quick_note WHERE id=@note;
UPDATE quick_note SET deleted=1,updated_by=2 WHERE id=@note AND version=1 AND deleted=0;
SELECT IF(ROW_COUNT()=1,'DELETE_OK','FAIL');
'@
  $tempResult = $tempSql | & $tempMysql @tempConnection --batch --skip-column-names --default-character-set=utf8mb4 quick_note_test
  if ($LASTEXITCODE -ne 0) { throw '关键结果验证失败' }
  $tempExpected = @('UPDATE_OK', 'CONFLICT_OK', 'JSON_OK', 'DELETE_OK')
  if (($tempResult -join ',') -ne ($tempExpected -join ',')) { throw ('结果不符合预期：' + ($tempResult -join ',')) }
  Write-Output ('PASS: ' + $tempMigrations.Count + ' migrations; JSON persistence, version conflict and direct deletion verified.')
} finally {
  if ($tempProcess -and !$tempProcess.HasExited) {
    & $tempMysqlAdmin @tempConnection shutdown 2>$null | Out-Null
    $tempProcess.WaitForExit(10000) | Out-Null
    if (!$tempProcess.HasExited) { Stop-Process -Id $tempProcess.Id }
  }
  # 日志留在 target 中供复查；不清理或移动用户的任何已有文件。
  Write-Output ('Test logs: ' + $tempWorkspace)
}
