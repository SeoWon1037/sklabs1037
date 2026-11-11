@echo off
rem ============================================
rem  SKLabs update helper  (run in the project folder:  .\update.cmd )
rem  1) finds the newest mock-trade-N.bundle in this folder
rem  2) git pull  -> 3) git push origin main -> 4) deletes the bundle files
rem  New SQL is run by the server itself (backend/src/main/resources/db/auto), so no pgAdmin step.
rem  If a step fails, nothing after it runs. You can always do the three steps by hand instead.
rem ============================================
setlocal enabledelayedexpansion

set MAX=0
for %%f in (mock-trade-*.bundle) do (
  set NAME=%%~nf
  set NUM=!NAME:mock-trade-=!
  if !NUM! GTR !MAX! set MAX=!NUM!
)

if "%MAX%"=="0" (
  echo [STOP] No mock-trade-N.bundle file in this folder.
  exit /b 1
)

echo [1/3] git pull mock-trade-%MAX%.bundle
git pull mock-trade-%MAX%.bundle mock-trade
if errorlevel 1 (
  echo [FAIL] pull failed. Nothing was pushed. Send a screenshot of the lines above.
  exit /b 1
)

echo [2/3] git push origin main
git push origin main
if errorlevel 1 (
  echo [FAIL] push failed. The bundle file is kept. Send a screenshot of the lines above.
  exit /b 1
)

echo [3/3] delete bundle files
del mock-trade-*.bundle

echo [DONE] Pushed bundle %MAX%. Deploy starts on GitHub Actions.
endlocal
