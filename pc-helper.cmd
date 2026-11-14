@echo off
rem SKLabs PC helper: lets the AI chatbot on sklabs1037.com launch programs installed on this PC.
rem Keep this window open. Close it to stop. Run "pc-helper.cmd --pair" to connect a browser again.
node "%~dp0tools\pc-helper\sklabs-pc-helper.js" %*
pause
