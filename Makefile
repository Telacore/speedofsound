APP_ID = io.speedofsound.SpeedOfSound
export GRADLE_OPTS = --enable-native-access=ALL-UNNAMED

SMOKE_TIMEOUT ?= 60
SMOKE_TIMEOUT_CINNAMON ?= 60
SMOKE_FAIL_ON_FATAL ?= false
SMOKE_FORCE_REMOTE_SESSION ?= false
SMOKE_TIMEOUT_CI ?= 60
SMOKE_FAIL_ON_FATAL_CI ?= true
SMOKE_FORCE_REMOTE_SESSION_CI ?= true
SMOKE_TIMEOUT_CI_MIN ?= 60

.PHONY: clean run run-light run-dark build shadow-build shadow-run check resources \
	meson-clean meson-setup meson-build meson-install uninstall install \
	flatpak-sources flatpak-linter flatpak-build flatpak-bundle flatpak-run flatpak-remove desktop-validate \
	snapcraft-clean snapcraft-pack snapcraft-lint snap-install snap-remove \
	jpackage-deb jpackage-rpm jpackage-app-image appimage \
	actionlint \
	smoke-startup smoke-startup-cinnamon \
	smoke-help \
	smoke-startup-ci \
	docs-serve docs-build

clean:
	./gradlew clean

run: clean
	SOS_DISABLE_GIO_STORE=true SOS_DISABLE_GSTREAMER=false ./gradlew :app:run

run-light: clean
	SOS_DISABLE_GIO_STORE=true SOS_DISABLE_GSTREAMER=false SOS_COLOR_SCHEME=light ./gradlew :app:run

run-dark: clean
	SOS_DISABLE_GIO_STORE=true SOS_DISABLE_GSTREAMER=false SOS_COLOR_SCHEME=dark ./gradlew :app:run

build:
	./gradlew build

shadow-build: clean
	./gradlew :app:shadowJar

shadow-run: shadow-build
	java --enable-native-access=ALL-UNNAMED -jar app/build/libs/speedofsound.jar

check:
	./gradlew check

smoke-startup:
	SMOKE_FAIL_ON_FATAL="$(SMOKE_FAIL_ON_FATAL)" \
	./scripts/smoke-startup.sh $(SMOKE_TIMEOUT)

smoke-startup-cinnamon:
	SMOKE_FORCE_REMOTE_SESSION="$(SMOKE_FORCE_REMOTE_SESSION)" \
	SMOKE_FAIL_ON_FATAL="$(SMOKE_FAIL_ON_FATAL)" \
	./scripts/smoke-startup-cinnamon.sh $(SMOKE_TIMEOUT_CINNAMON)

smoke-help:
	@printf '%s\n' \
	"Smoke targets:" \
	"  make smoke-startup" \
	"    Timeout:  $(SMOKE_TIMEOUT) seconds" \
	"    Force fatal fail: $(SMOKE_FAIL_ON_FATAL)" \
	"  make smoke-startup-cinnamon" \
	"    Timeout:  $(SMOKE_TIMEOUT_CINNAMON) seconds" \
	"    Force remote session: $(SMOKE_FORCE_REMOTE_SESSION)" \
	"    Force fatal fail: $(SMOKE_FAIL_ON_FATAL)" \
	"  make smoke-startup-ci" \
	"    Timeout:  $(SMOKE_TIMEOUT_CI) seconds" \
	"    Cinnamon timeout minimum: $(SMOKE_TIMEOUT_CI_MIN) seconds" \
	"    Force fatal fail: $(SMOKE_FAIL_ON_FATAL_CI)" \
	""

smoke-startup-ci:
	@echo "Running CI startup smoke checks with timeout $(SMOKE_TIMEOUT_CI) and fatal-on-error $(SMOKE_FAIL_ON_FATAL_CI)."
	@ci_run_id=$$(date +%s)-$$RANDOM; \
	ci_timeout_raw="$(SMOKE_TIMEOUT_CI)"; \
	ci_timeout_min_raw="$(SMOKE_TIMEOUT_CI_MIN)"; \
	case "$$ci_timeout_raw" in \
	  ""|*[!0-9]*) \
	    echo "Invalid SMOKE_TIMEOUT_CI='$$ci_timeout_raw'; defaulting to 60."; \
	    ci_timeout_raw=60; \
	    ;; \
	esac; \
	case "$$ci_timeout_min_raw" in \
	  ""|*[!0-9]*) \
	    echo "Invalid SMOKE_TIMEOUT_CI_MIN='$$ci_timeout_min_raw'; defaulting to 60."; \
	    ci_timeout_min_raw=60; \
	    ;; \
	esac; \
	ci_cinnamon_timeout="$$ci_timeout_raw"; \
	if [ "$$ci_cinnamon_timeout" -lt "$$ci_timeout_min_raw" ]; then \
	  ci_cinnamon_timeout="$$ci_timeout_min_raw"; \
	fi; \
	SMOKE_TIMEOUT="$$ci_timeout_raw" \
	SMOKE_FAIL_ON_FATAL="$(SMOKE_FAIL_ON_FATAL_CI)" \
	SMOKE_LOG_FILE="/tmp/speedofsound-smoke-startup-$$ci_run_id-main.log" \
	$(MAKE) smoke-startup && \
	SMOKE_FORCE_REMOTE_SESSION="$(SMOKE_FORCE_REMOTE_SESSION_CI)" \
	SMOKE_TIMEOUT_CINNAMON="$$ci_cinnamon_timeout" \
	SMOKE_FAIL_ON_FATAL="$(SMOKE_FAIL_ON_FATAL_CI)" \
	SMOKE_LOG_FILE="/tmp/speedofsound-smoke-startup-$$ci_run_id-cinnamon.log" \
	$(MAKE) smoke-startup-cinnamon

resources:
	rm -f app/src/main/resources/speedofsound.gresource
	./gradlew :app:compileResources

#
# Meson build
#

meson-clean:
	rm -rf builddir

meson-setup: meson-clean
	meson setup builddir --prefix=$(HOME)/.local

meson-build:
	ninja -C builddir

meson-install:
	ninja -C builddir install

uninstall:
	ninja -C builddir uninstall

install: meson-setup meson-build meson-install

#
# Flatpak
#

flatpak-sources:
	rm -f buildSrc/flatpak-sources.json core/flatpak-sources.json app/flatpak-sources.json
	./gradlew --project-dir buildSrc flatpakGradleGenerator --no-configuration-cache
	./gradlew :app:flatpakGradleGenerator :core:flatpakGradleGenerator --no-configuration-cache

flatpak-linter:
	flatpak run --command=flatpak-builder-lint org.flatpak.Builder appstream data/$(APP_ID).metainfo.xml.in
	flatpak run --command=flatpak-builder-lint org.flatpak.Builder manifest $(APP_ID).yml

flatpak-clean:
	rm -rf builddir repo

flatpak-build:
	flatpak-builder --force-clean --user --install-deps-from=flathub --repo=repo --install builddir $(APP_ID).yml

flatpak-bundle:
	rm -f speedofsound.flatpak
	flatpak build-bundle repo speedofsound.flatpak $(APP_ID) --runtime-repo=https://flathub.org/repo/flathub.flatpakrepo

flatpak-run:
	flatpak run $(APP_ID)

flatpak-remove:
	flatpak remove --user $(APP_ID)

desktop-validate:
	desktop-file-validate data/$(APP_ID).desktop.in

#
# Snap
#

snapcraft-clean:
	snapcraft clean

snapcraft-pack:
	rm -f speedofsound_*.snap
	snapcraft pack

snapcraft-lint:
	snapcraft lint speedofsound_*.snap

snap-install:
	snap install speedofsound_*_amd64.snap --dangerous
	snap connect speedofsound:audio-record
	snap connect speedofsound:alsa

snap-remove:
	snap remove speedofsound

#
# GitHub Actions
#

actionlint:
	actionlint -verbose

#
# jpackage
#

jpackage-deb:
	rm -rf app/build/jpackage
	./gradlew :app:jpackage-deb --no-configuration-cache

jpackage-rpm:
	rm -rf app/build/jpackage
	./gradlew :app:jpackage-rpm --no-configuration-cache

jpackage-app-image:
	rm -rf app/build/jpackage
	./gradlew :app:jpackage-app-image --no-configuration-cache

appimage: jpackage-app-image
	./scripts/build-appimage.sh


#
# Docs
#

docs-serve:
	mkdocs serve

docs-build:
	mkdocs build
