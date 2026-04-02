@echo off
cd /d c:\xampp\htdocs\Projects\Spend-Vista---Android
echo Syncing assets...
call npx cap sync android
echo Building and installing...
call npx cap run android
pause
