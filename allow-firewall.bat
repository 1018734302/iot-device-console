@echo off
net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)
netsh advfirewall firewall delete rule name="IoT Demo 8081" >nul 2>&1
netsh advfirewall firewall add rule name="IoT Demo 8081" dir=in action=allow protocol=TCP localport=8081
netsh advfirewall firewall delete rule name="IoT Demo 8083" >nul 2>&1
netsh advfirewall firewall add rule name="IoT Demo 8083" dir=in action=allow protocol=TCP localport=8083
echo.
echo Firewall rules added: TCP 8081, TCP 8083
pause
