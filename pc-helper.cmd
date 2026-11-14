@echo off
rem SKLabs PC helper: lets the AI chatbot on sklabs1037.com launch programs installed on this PC.
rem Keep this window open. Close it to stop. Options: --pair (connect a browser again), --mouse (voice mouse), --autostart / --no-autostart (start when you sign in to Windows).
node "%~dp0tools\pc-helper\sklabs-pc-helper.js" %*
pause
