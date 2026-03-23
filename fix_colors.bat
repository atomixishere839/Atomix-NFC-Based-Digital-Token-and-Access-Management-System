@echo off
echo Fixing color attributes in layout files...

cd "c:\Users\pande\OneDrive\Desktop\Sahil project nfc\safe1\atomix\app\src\main\res\layout"

for %%f in (*.xml) do (
    powershell -Command "(Get-Content '%%f') -replace '@color/dark_background', '?android:attr/colorBackground' -replace '@color/dark_card', '?attr/colorSurface' -replace '@color/dark_primary', '?attr/colorPrimary' -replace '@color/dark_secondary', '?attr/colorSecondary' -replace '@color/dark_text_primary', '?android:attr/textColorPrimary' -replace '@color/dark_text_secondary', '?android:attr/textColorSecondary' -replace '@color/dark_error', '?attr/colorError' -replace '@color/dark_success', '@color/status_active' | Set-Content '%%f'"
)

echo Done!
pause
