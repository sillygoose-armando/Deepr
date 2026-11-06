# Developer shortcuts. Run `make help` to list them. iOS targets need a Mac with Xcode (Apple Silicon).
SHELL := /bin/bash
.DEFAULT_GOAL := help
.PHONY: help setup ios ios-open ios-test desktop apk

REPO := sillygoose-armando/Deepr
# First available iPhone simulator, e.g. "iPhone 17".
DEVICE = ${shell xcrun simctl list devices available 2>/dev/null | grep -m1 -oE 'iPhone [0-9]+[^(]*' | sed 's/ *$$//'}
# Gradle (also run by Xcode) needs JDK 21.
export JAVA_HOME := $(shell /usr/libexec/java_home -v 21 2>/dev/null || echo $$JAVA_HOME)

help: ## List the commands
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  make %-10s %s\n", $$1, $$2}'

setup: ## One-time Mac setup: xcodegen, JDK 21, point the tools at Xcode, iOS simulator
	brew list xcodegen >/dev/null 2>&1 || brew install xcodegen
	/usr/libexec/java_home -v 21 >/dev/null 2>&1 || brew install --cask temurin@21
	@test -d /Applications/Xcode.app || { echo "Install Xcode from the App Store first."; exit 1; }
	sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
	sudo xcodebuild -runFirstLaunch
	xcrun simctl list devices available | grep -q iPhone || xcodebuild -downloadPlatform iOS

iosApp/Deepr.xcodeproj: iosApp/project.yml
	cd iosApp && xcodegen generate

ios: iosApp/Deepr.xcodeproj ## Regenerate the Xcode project (after each git pull), then build/run it in Xcode
	@echo ""
	@echo "Xcode project is up to date: iosApp/Deepr.xcodeproj"
	@echo "  1. Open it in Xcode (double-click it, or: make ios-open)"
	@echo "  2. At the top of the window, pick the 'Deepr' scheme and an iPhone simulator"
	@echo "  3. Press Run (the ▶ button, or Cmd+R): Xcode builds the app, starts the simulator and launches Deepr"

ios-open: iosApp/Deepr.xcodeproj ## Open the iOS project in Xcode (then press Run, the ▶ button)
	open iosApp/Deepr.xcodeproj

ios-test: iosApp/Deepr.xcodeproj ## Run the iOS UI test on the simulator
	@test -n "$(DEVICE)" || { echo "No iPhone simulator found: run 'make setup'."; exit 1; }
	xcrun simctl boot "$(DEVICE)" 2>/dev/null || true
	xcrun simctl bootstatus "$(DEVICE)" -b
	xcodebuild -project iosApp/Deepr.xcodeproj -scheme Deepr -configuration Debug \
	  -destination "platform=iOS Simulator,name=$(DEVICE)" -derivedDataPath iosApp/build test 2>&1 | tail -25

desktop: ## Run the desktop app
	./gradlew :desktopApp:run

apk: ## Download the newest green Android debug APK from CI and install it on a USB-connected phone
	@id=$$(gh run list -R $(REPO) --workflow p2p-build.yml --branch feature/kmp --status success --limit 1 --json databaseId --jq '.[0].databaseId'); \
	  echo "CI run $$id"; rm -rf build/apk; \
	  gh run download $$id -R $(REPO) -n deepr-p2p-debug-apk -D build/apk && adb install -r build/apk/*.apk
