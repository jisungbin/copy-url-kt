#!/bin/bash
# CopyUrl 재배포: 컴파일 → jpackage(.app) → /Applications 설치 → 실행.
# 사용법: ./build_app.sh
set -e
cd "$(dirname "$0")"

echo "[0] 앱 아이콘 생성"
python3 gen_appicon.py
iconutil -c icns build/appicon/CopyUrl.iconset -o build/appicon/CopyUrl.icns

echo "[1] 컴파일 + 의존성 모으기 (installDist)"
./gradlew installDist --console=plain -q
MAINJAR=$(ls build/install/copy-url-kt/lib/ | grep "copy-url-kt" | head -1)
echo "    main jar: $MAINJAR"

echo "[2] jpackage (.app 빌드)"
JP="$(/usr/libexec/java_home -v 21)/bin/jpackage"
rm -rf build/jpackage
"$JP" --type app-image --name CopyUrl --app-version 1.0.0 \
  --input build/install/copy-url-kt/lib \
  --main-jar "$MAINJAR" \
  --main-class dev.jisungbin.copyurl.MainKt \
  --java-options -XstartOnFirstThread \
  --mac-package-identifier dev.jisungbin.copyurl \
  --icon build/appicon/CopyUrl.icns \
  --dest build/jpackage

echo "[3] LSUIElement 추가 (Dock 미표시)"
/usr/libexec/PlistBuddy -c "Add :LSUIElement bool true" \
  build/jpackage/CopyUrl.app/Contents/Info.plist 2>/dev/null || true

echo "[4] 기존 앱 종료 + /Applications 교체"
pkill -9 -f "CopyUrl" 2>/dev/null || true
rm -rf /Applications/CopyUrl.app
cp -R build/jpackage/CopyUrl.app /Applications/
# 자체 서명 — ad-hoc 으로는 자동화(크롬 제어) 권한을 못 받으므로 안정적 식별을 위해 재서명.
# 같은 인증서로 서명하면 designated requirement 가 동일해 권한이 유지된다.
codesign --force --deep -s "CopyUrl Self-Signed" /Applications/CopyUrl.app 2>/dev/null \
  && echo "    → 자체 서명 적용 (자동화 권한 유지용)" \
  || echo "    → ⚠️ 서명 실패: 'CopyUrl Self-Signed' 인증서가 키체인에 있는지 확인"
xattr -dr com.apple.quarantine /Applications/CopyUrl.app 2>/dev/null || true

echo "[5] 실행"
open /Applications/CopyUrl.app
echo "✅ 완료: /Applications/CopyUrl.app (로그인 시 자동 시작 등록은 최초 1회만 필요)"
