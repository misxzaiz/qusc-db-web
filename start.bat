@echo off
echo Starting DB Admin...

echo Starting Backend...
cd backend
start "Backend" cmd /c "mvn spring-boot:run"

echo Waiting for backend to start...
timeout /t 10 /nobreak

echo Starting Frontend...
cd ..\frontend
start "Frontend" cmd /c "npm run dev"

echo Services started!
echo Backend: http://localhost:8080
echo Frontend: http://localhost:3000
pause